package io.workshop.auction.entity.auction

import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, Routers }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

import java.util.UUID

object AuctionEntity {
  type Lots = Map[UUID, ActorRef[LotCommand]]

  def apply(id: UUID): Behavior[AuctionCommand] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Setting up auction with id: ${id.toString}")

      val serviceKey = ServiceKey[LotWorkerCommand](id.toString)
      val worker     = ctx.spawn(LotWorkerActor(), "lot-worker")
      ctx.system.receptionist ! Receptionist.Register(serviceKey, worker)

      val group  = Routers.group(serviceKey)
      val router = ctx.spawn(group, "lot-worker-group")

      EventSourcedBehavior[AuctionCommand, AuctionEvent, AuctionState](
        PersistenceId.ofUniqueId(id.toString),
        emptyState     = auction.ClosedState(id),
        commandHandler = (state, cmd) => onCommandHandler(state, cmd, ctx, router),
        eventHandler   = (state, event) => onEventHandler(state, event, ctx)
      )
    }

  private def onCommandHandler(
    auState: AuctionState,
    cmd: AuctionCommand,
    ctx: ActorContext[AuctionCommand],
    router: ActorRef[LotWorkerCommand]
  ): Effect[AuctionEvent, AuctionState] =
    auState match {
      case ClosedState(_, lots, state) =>
        cmd match {
          case AddLot(replyTo, details)  => onAddLotCommand(ctx, details, replyTo)
          case RemoveLot(lotId, replyTo) => onLotRemoveCommand(ctx, lots, lotId, replyTo)
          case StartAuction(replyTo)     => onStartAuctionCommand(ctx, lots, replyTo)
          case GetAuction(name, replyTo) => onGetAuctionCommand(ctx, name, replyTo)
          case _                         => onReadOnlyCommand(cmd, ctx, state)
        }
      case InProgressState(_, lots, state) =>
        cmd match {
          case PlaceBid(lotId, bid, replyTo) => onPlaceBidCommand(ctx, lots, router, lotId, bid, replyTo)
          case GetLot(lotId, replyTo)        => onGetLotCommand(ctx, lots, router, lotId, replyTo)
          case GetAllLots(replyTo)           => onGetAllLotsCommand(ctx, lots, router, replyTo)
          case SubscribeLot(lotId, replyTo)  => onSubscribeLotCommand(ctx, lots, router, lotId, replyTo)
          case EndAuction(replyTo)           => onEndAuctionCommand(ctx, lots, router, replyTo)
          case GetAuction(name, replyTo)     => onGetAuctionCommand(ctx, name, replyTo)
          case _                             => onReadOnlyCommand(cmd, ctx, state)
        }
      case FinishedState(_, _, state) =>
        cmd match {
          case GetAuction(name, replyTo) => onGetAuctionCommand(ctx, name, replyTo)
          case _                         => onReadOnlyCommand(cmd, ctx, state)
        }
    }

  private def onEventHandler(
    state: AuctionState,
    event: AuctionEvent,
    ctx: ActorContext[AuctionCommand]
  ): AuctionState =
    state match {
      case cs @ ClosedState(_, lots, _) =>
        event match {
          case LotAdded(lotId, details) =>
            val lotActorRef = ctx.spawn(LotEntity(lotId, details.initialPrice, details.maxPrice), s"lot-$lotId")
            cs.copy(lots = lots + (lotId -> lotActorRef))
          case LotRemoved(lotId) =>
            cs.copy(lots = lots - lotId)
          case AuctionStarted => InProgressState(cs.id, cs.lots)
          case _              => cs
        }
      case ips @ InProgressState(_, _, _) =>
        event match {
          case AuctionEnded =>
            auction.FinishedState(ips.id, Map.empty)
          case _ => ips
        }
      case fs @ FinishedState(_, _, _) =>
        event match {
          case _ => fs
        }
    }

  private def onGetAuctionCommand(
    ctx: ActorContext[AuctionCommand],
    name: String,
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] = {
    ctx.log.debug("auction getting")
    Effect.none.thenReply(replyTo)(currentState => GotAuction(currentState.id, name, currentState.state))
  }

  private def onStartAuctionCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    replyTo: ActorRef[AuctionEvent]
  ): Effect[AuctionEvent, AuctionState] = {
    ctx.log.debug("auction starting")
    lots match {
      case m if m.isEmpty =>
        Effect.unhandled.thenReply(replyTo)(_ => AuctionNotStarted("Could not find any lot to start auction"))
      case _ => Effect.persist(AuctionStarted).thenReply(replyTo)(_ => AuctionStarted)
    }
  }

  private def onEndAuctionCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    router: ActorRef[LotWorkerCommand],
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] = {
    ctx.log.debug("auction ending")
    router ! CloseLots(lots, replyTo)
    Effect.persist(AuctionEnded).thenReply(replyTo)(_ => AuctionEnded)
  }

  private def onSubscribeLotCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    router: ActorRef[LotWorkerCommand],
    lotId: UUID,
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] =
    validate(ctx, lots, lotId, replyTo) { lotRef =>
      ctx.log.debug(s"Subscribing to lot lotId: $lotId")
      router ! ForwardSubscriber(lotId, lotRef, replyTo)
      Effect.none
    }

  private def onGetAllLotsCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    router: ActorRef[LotWorkerCommand],
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] = {
    ctx.log.debug(s"Getting all lots of the auction")
    router ! CollectAllLotsSummary(lots, replyTo)
    Effect.none
  }

  private def onGetLotCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    router: ActorRef[LotWorkerCommand],
    lotId: UUID,
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] =
    validate(ctx, lots, lotId, replyTo) { lotRef =>
      ctx.log.debug(s"Getting lot from the auction by lotId: $lotId")
      router ! CollectLotSummary(lotId, lotRef, replyTo)
      Effect.none
    }

  private def onPlaceBidCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    router: ActorRef[LotWorkerCommand],
    lotId: UUID,
    bid: Bid,
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] =
    validate(ctx, lots, lotId, replyTo) { lotRef =>
      ctx.log.debug(s"Placing bid to the lot with lotId: $lotId")
      router ! OfferBid(lotId, bid, lotRef, replyTo)
      Effect.none
    }

  private def onLotRemoveCommand(
    ctx: ActorContext[AuctionCommand],
    lots: Lots,
    lotId: UUID,
    replyTo: ActorRef[Event]
  ): Effect[AuctionEvent, AuctionState] = {
    ctx.log.debug(s"Removing lot from the auction by lotId: $lotId")
    validate(ctx, lots, lotId, replyTo) { lotRef =>
      ctx.stop(lotRef)
      val event = LotRemoved(lotId)
      Effect.persist(event).thenReply(replyTo)((_: AuctionState) => event)
    }
  }

  private def onAddLotCommand(
    ctx: ActorContext[AuctionCommand],
    details: LotDetails,
    replyTo: ActorRef[AuctionEvent]
  ): Effect[AuctionEvent, AuctionState] = {
    val lotId = UUID.randomUUID()
    ctx.log.debug(s"Adding lot to the auction by lotId: $lotId")

    val event = LotAdded(lotId, details)
    Effect.persist(event).thenReply(replyTo)(_ => event)
  }

  private def onReadOnlyCommand(
    command: AuctionCommand,
    ctx: ActorContext[AuctionCommand],
    currentState: States.Value
  ): Effect[AuctionEvent, AuctionState] =
    command match {
      case AddLot(replyTo, _) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate adding lot request cause of current state is $currentState")
        )
      case RemoveLot(_, replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate removing lot request cause of current state is $currentState")
        )
      case StartAuction(replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate starting auction request cause of current state is $currentState")
        )
      case PlaceBid(_, _, replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate placing bid request cause of current state is $currentState")
        )
      case GetLot(_, replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate get lot request cause of current state is $currentState")
        )
      case GetAllLots(replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate get all lots request cause of current state is $currentState")
        )
      case EndAuction(replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate ending auction request cause of current state is $currentState")
        )
      case SubscribeLot(_, replyTo) =>
        Effect.unhandled.thenReply(replyTo)(_ =>
          ReadOnlyCommandReceived(s"Could not operate subscribe lot request cause of current state is $currentState")
        )
      case _ =>
        Effect.unhandled
    }

  private def validate(ctx: ActorContext[AuctionCommand], lots: Lots, lotId: UUID, replyTo: ActorRef[Event])(
    f: ActorRef[LotCommand] => Effect[AuctionEvent, AuctionState]
  ): Effect[AuctionEvent, AuctionState] =
    lots.get(lotId) match {
      case Some(lot) => f(lot)
      case None =>
        ctx.log.debug(s"Could not find lot by id $lotId")
        replyTo ! LotNotFound(s"Could not find lot by id $lotId")
        Effect.none
    }
}
