package io.workshop.auction.service.auction

import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.{ Done, NotUsed }

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

class AuctionServiceImpl(auctionManager: ActorRef[AuctionManagerCommand])(implicit system: ActorSystem[_])
    extends AuctionService {

  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  implicit val timeout: Timeout                           = 3.seconds

  override def createAuction(auction: CreateAuctionRequest): Future[AuctionIdResponse] =
    auctionManager
      .ask[AuctionManagerEvent](ref => CreateAuction(auction.name, ref))
      .mapTo[AuctionCreated]
      .map(auction => AuctionIdResponse(auction.auctionId))

  override def getAuctions: Source[AuctionResponse, Future[NotUsed]] = {
    val auctionsFuture = auctionManager
      .ask[Event](ref => ListAuctions(ref))
      .map {
        case received @ AuctionsReceived(_) =>
          Source
            .apply(received.auctions.toList)
            .map(summary => AuctionResponse(summary.id, summary.name, summary.status))
        case ex @ AuctionsNotReceivedException(_, _) => throw ex
        case _                                       => throw new Exception("Ups! Something went wrong!")
      }
    Source.futureSource(auctionsFuture)
  }

  override def startAuction(auctionId: UUID): Future[Try[Done]] =
    auctionManager
      .ask[AuctionEvent](ref => WrappedAuctionCommand(auctionId, StartAuction(ref)))
      .map {
        case AuctionStarted               => Success(Done)
        case AuctionNotStarted(msg)       => Failure(IllegalAuctionStateException(msg))
        case AuctionNotFound(msg)         => Failure(EntityNotFoundException(msg))
        case ReadOnlyCommandReceived(msg) => Failure(IllegalAuctionStateException(msg))
        case _                            => Failure(new RuntimeException("Ups! Something went wrong!"))
      }

  override def endAuction(auctionId: UUID): Future[Try[Done]] =
    auctionManager
      .ask[Event](ref => WrappedAuctionCommand(auctionId, EndAuction(ref)))
      .map {
        case AuctionEnded                 => Success(Done)
        case AuctionNotFound(msg)         => Failure(EntityNotFoundException(msg))
        case ReadOnlyCommandReceived(msg) => Failure(IllegalAuctionStateException(msg))
        case _                            => Failure(new RuntimeException("Ups! Something went wrong!"))
      }
}
