package io.workshop.auction.entity.manager

import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object AuctionManagerEntity {
  type NamedAuction = (String, ActorRef[AuctionCommand])
  type Auctions     = Map[UUID, NamedAuction]

  def apply(entityId: String): Behavior[AuctionManagerCommand] = Behaviors.setup { ctx =>
    EventSourcedBehavior[AuctionManagerCommand, AuctionManagerEvent, AuctionManagerState](
      PersistenceId.ofUniqueId(entityId),
      emptyState     = manager.AuctionManagerState(entityId),
      commandHandler = (state, cmd) => onCommandHandler(state, cmd, ctx),
      eventHandler   = (state, event) => onEventHandler(state, event, ctx)
    )
  }

  def onCommandHandler(
    state: AuctionManagerState,
    cmd: AuctionManagerCommand,
    ctx: ActorContext[AuctionManagerCommand]
  ): Effect[AuctionManagerEvent, AuctionManagerState] =
    cmd match {
      case CreateAuction(name, replyTo) =>
        val auctionId = UUID.randomUUID()
        val event     = AuctionCreated(name, auctionId)
        Effect.persist(event).thenReply(replyTo)(_ => event)
      case ListAuctions(replyTo) =>
        implicit val system: ActorSystem[_]       = ctx.system
        implicit val ec: ExecutionContextExecutor = system.executionContext
        implicit val timeout: Timeout             = 3.seconds

        Source(state.auctions)
          .mapAsync(1)(auction => auction._2._2.ask[Event](ref => GetAuction(auction._2._1, ref)))
          .collect { case auction @ GotAuction(_, _, _) => auction }
          .runWith(Sink.seq[GotAuction])
          .onComplete {
            case Success(value) => replyTo ! AuctionsReceived(value)
            case Failure(exception) =>
              ctx.log.error(s"Error occurred while collecting the auctions", exception)
              replyTo ! AuctionsNotReceivedException("Error occurred while collecting the auctions", exception)
          }

        Effect.none
      case WrappedAuctionCommand(auctionId, command) =>
        state.auctions.get(auctionId) match {
          case Some(auction) => auction._2 ! command
          case None          => command.replyTo ! AuctionNotFound(s"Could not find auction by id $auctionId")
        }
        Effect.none
    }

  def onEventHandler(
    state: AuctionManagerState,
    event: AuctionManagerEvent,
    ctx: ActorContext[AuctionManagerCommand]
  ): AuctionManagerState =
    event match {
      case AuctionCreated(name, auctionId) =>
        val auctionActorRef = ctx.spawn(AuctionEntity(auctionId), s"auction-$auctionId")
        state.copy(auctions = state.auctions + (auctionId -> (name, auctionActorRef)))
      case _ => state
    }
}
