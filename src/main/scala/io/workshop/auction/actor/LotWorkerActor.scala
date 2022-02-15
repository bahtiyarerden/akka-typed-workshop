package io.workshop.auction.actor

import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object LotWorkerActor {

  def apply(): Behavior[LotWorkerCommand] = Behaviors.setup { ctx =>
    behavior(ctx)
  }

  def behavior(ctx: ActorContext[LotWorkerCommand]): Behavior[LotWorkerCommand] = Behaviors.receiveMessagePartial {
    case OfferBid(lotId, bid, lot, replyTo) =>
      lot ! MakeBid(lotId, bid, replyTo)
      Behaviors.same
    case CollectLotSummary(lotId, lot, replyTo) =>
      lot ! GetLotSummary(lotId, replyTo)
      Behaviors.same
    case CollectAllLotsSummary(lots, replyTo) =>
      onCollectAllLotsCommand(ctx, lots, replyTo)
      Behaviors.same
    case ForwardSubscriber(lotId, lot, replyTo) =>
      lot ! Subscribe(lotId, replyTo)
      Behaviors.same
    case CloseLots(lots, replyTo) =>
      lots.foreach(lotMap => lotMap._2 ! Close(lotMap._1, replyTo))
      Behaviors.same
  }

  private def onCollectAllLotsCommand(
    ctx: ActorContext[LotWorkerCommand],
    lots: Lots,
    replyTo: ActorRef[Event]
  ): Unit = {
    implicit val system: ActorSystem[_]       = ctx.system
    implicit val ec: ExecutionContextExecutor = system.executionContext
    implicit val timeout: Timeout             = 3.seconds

    ctx.log.debug(s"Getting all lots from the auction, ${lots.toString()}")

    Source(lots.toList)
      .mapAsync(1)(lot => lot._2.ask[Event](ref => GetLotSummary(lot._1, ref)))
      .collect { case lot @ LotSummaryGotten(_, _, _) => lot }
      .runWith(Sink.seq[LotSummaryGotten])
      .onComplete {
        case Success(value) => replyTo ! LotsReceived(value)
        case Failure(exception) =>
          ctx.log.error(s"Error occurred while collecting the lots", exception)
          replyTo ! LotsNotReceivedException("Error occurred while collecting the lots", Some(exception))
      }
  }
}
