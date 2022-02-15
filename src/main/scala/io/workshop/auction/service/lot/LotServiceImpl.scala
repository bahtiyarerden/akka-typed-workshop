package io.workshop.auction.service.lot

import akka.actor.typed.scaladsl.AskPattern.{ schedulerFromActorSystem, Askable }
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.{ Done, NotUsed }

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

class LotServiceImpl(auctionManager: ActorRef[AuctionManagerCommand])(implicit system: ActorSystem[_])
    extends LotService {
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  implicit val timeout: Timeout                           = 3.seconds

  override def createLot(auctionId: UUID, details: CreateLotRequest): Future[Try[LotIdResponse]] =
    auctionManager
      .ask[AuctionEvent](ref =>
        WrappedAuctionCommand(auctionId, AddLot(ref, LotDetails(details.initialPrice, details.maxPrice)))
      )
      .map {
        case LotAdded(lotId, _)           => Success(LotIdResponse(lotId))
        case LotNotFound(msg)             => Failure(EntityNotFoundException(msg))
        case ReadOnlyCommandReceived(msg) => Failure(IllegalAuctionStateException(msg))
        case _                            => Failure(new RuntimeException("Ups! Something went wrong!"))
      }

  override def makeBid(auctionId: UUID, lotId: UUID, userId: UUID, bid: BidRequest): Future[Try[Done]] =
    auctionManager
      .ask[Event](ref => WrappedAuctionCommand(auctionId, PlaceBid(lotId, Bid(userId, bid.bid, bid.maxBid), ref)))
      .map {
        case BidMade(_, _, _)             => Success(Done)
        case BidNotMade(msg)              => Failure(UnfitBidException(msg))
        case LotNotFound(msg)             => Failure(EntityNotFoundException(msg))
        case ReadOnlyCommandReceived(msg) => Failure(IllegalAuctionStateException(msg))
        case _                            => Failure(new RuntimeException("Ups! Something went wrong!"))
      }

  override def getLotsByAuction(auctionId: UUID): Future[Try[Source[LotResponse, NotUsed]]] =
    auctionManager
      .ask[Event](ref => WrappedAuctionCommand(auctionId, GetAllLots(ref)))
      .map {
        case received @ LotsReceived(_) =>
          Success(
            Source
              .apply(received.lots.toList)
              .map(summary => LotResponse(auctionId, summary.lotId, summary.totalBidCount, summary.bid))
          )
        case ReadOnlyCommandReceived(msg)        => Failure(IllegalAuctionStateException(msg))
        case ex @ LotsNotReceivedException(_, _) => Failure(ex)
        case _                                   => Failure(new RuntimeException("Ups! Something went wrong!"))
      }

  override def getLotById(auctionId: UUID, lotId: UUID): Future[Try[LotResponse]] =
    auctionManager
      .ask[Event](ref => WrappedAuctionCommand(auctionId, GetLot(lotId, ref)))
      .map {
        case LotSummaryGotten(lotId, totalBidCount, bid) => Success(LotResponse(auctionId, lotId, totalBidCount, bid))
        case LotNotFound(msg)                            => Failure(EntityNotFoundException(msg))
        case ReadOnlyCommandReceived(msg)                => Failure(IllegalAuctionStateException(msg))
        case _                                           => Failure(new RuntimeException("Ups! Something went wrong!"))
      }
}
