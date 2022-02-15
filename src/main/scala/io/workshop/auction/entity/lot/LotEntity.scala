package io.workshop.auction.entity.lot

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

import java.util.UUID

object LotEntity {

  def apply(entityId: UUID, initialPrice: Int = 0, maxPrice: Option[Int] = None): Behavior[LotCommand] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior[LotCommand, LotEvent, LotState](
        persistenceId = PersistenceId.ofUniqueId(entityId.toString),
        emptyState    = LotState(entityId, initialPrice = initialPrice, maxPrice = maxPrice, winningBid = initialPrice),
        commandHandler = (state, command) => onCommandHandler(state, command, ctx),
        eventHandler   = (state, event) => onEventHandler(state, event, ctx)
      )
    }

  private def onCommandHandler(
    state: LotState,
    command: LotCommand,
    ctx: ActorContext[LotCommand]
  ): Effect[LotEvent, LotState] =
    command match {
      case MakeBid(lotId, bid, replyTo)  => onMakeBidCommand(lotId, bid, replyTo)(state, ctx)
      case GetLotSummary(lotId, replyTo) => onGetLotSummaryCommand(lotId, replyTo)(state, ctx)
      case Subscribe(lotId, replyTo)     => onSubscribeCommand(lotId, replyTo)(ctx)
      case Close(lotId, replyTo)         => onCloseCommand(lotId, replyTo)(ctx)
    }

  private def onEventHandler(state: LotState, event: LotEvent, ctx: ActorContext[LotCommand]): LotState =
    event match {
      case BidMade(_, bid, winningBid) => state.copy(bids = state.bids.+:(bid), winningBid = winningBid)
      case Subscribed(subscriber)      => state.copy(subscribers = state.subscribers.+:(subscriber))
      case Closed                      => state.copy(bids = List.empty, winningBid = 0)
      case _                           => state
    }

  private def onMakeBidCommand(lotId: UUID, bid: Bid, replyTo: ActorRef[Event])(
    state: LotState,
    ctx: ActorContext[LotCommand]
  ): Effect[LotEvent, LotState] = {
    ctx.log.debug(s"Making bid: ${bid.bid} with max: ${bid.maxBid} to lot:${ctx.self.path} ")
    bid match {
      case Bid(_, _, _) if state.maxPrice.isDefined && state.maxPrice.get == state.winningBid =>
        Effect.none.thenReply(replyTo)(_ =>
          BidNotMade(s"Can not bidding to lot: $lotId. The maximum bid has been reached. Thanks for joining")
        )
      case Bid(_, bidValue, None) if state.winningBid < bidValue =>
        val event = state.maxPrice match {
          case Some(maxPrice) if bidValue > maxPrice => BidMade(lotId, bid.copy(bid = maxPrice), maxPrice)
          case _                                     => BidMade(lotId, bid, bidValue)
        }
        Effect
          .persist(event)
          .thenRun { _ =>
            val replies = state.subscribers :+ replyTo
            replies.foreach(ref => ref ! event)
          }
      case Bid(_, bidValue, Some(maxBid)) if state.winningBid < maxBid =>
        val event = state.maxPrice match {
          case Some(maxPrice) if bidValue > maxPrice || maxBid > maxPrice =>
            val updatedBid = bid.copy(bid = maxPrice)
            BidMade(lotId, updatedBid, maxPrice)
          case _ =>
            val lowestPossibleValue = if (bidValue > state.winningBid) bidValue else state.winningBid + 1
            val updatedBid          = bid.copy(bid = lowestPossibleValue)
            BidMade(lotId, updatedBid, maxBid)
        }

        Effect
          .persist(event)
          .thenRun { _ =>
            val replies = state.subscribers :+ replyTo
            replies.foreach(ref => ref ! event)
          }
      case _ =>
        Effect.none.thenReply(replyTo)(_ =>
          BidNotMade(s"Can not bidding to lot: $lotId its maximum bid is ${state.winningBid}")
        )
    }
  }

  private def onGetLotSummaryCommand(
    lotId: UUID,
    replyTo: ActorRef[Event]
  )(state: LotState, ctx: ActorContext[LotCommand]): Effect[LotEvent, LotState] = {
    ctx.log.debug(s"getting lot summary ${state.id}, ${state.bids.toString()}, ${ctx.self.path}")
    state.bids.headOption match {
      case bid @ Some(_) =>
        Effect.reply(replyTo)(LotSummaryGotten(state.id, state.bids.length, bid))
      case None =>
        Effect.reply(replyTo)(LotSummaryGotten(state.id, state.bids.length, None))
    }
  }

  private def onSubscribeCommand(lotId: UUID, replyTo: ActorRef[Event])(
    ctx: ActorContext[LotCommand]
  ): Effect[LotEvent, LotState] =
    Effect.persist(Subscribed(replyTo)).thenReply(replyTo)(_ => Subscribed(replyTo))

  private def onCloseCommand(lotId: UUID, replyTo: ActorRef[Event])(
    ctx: ActorContext[LotCommand]
  ): Effect[LotEvent, LotState] =
    Effect
      .persist(Closed)
      .thenRun { (state: LotState) =>
        val replies = state.subscribers
        replies.foreach(ref => ref ! Closed)
      }
      .thenStop()
}
