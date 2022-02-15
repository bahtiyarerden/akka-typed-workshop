package io.workshop.auction.service.ws

import akka.NotUsed
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink }
import akka.stream.typed.scaladsl.ActorSource

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor

class BiddingWebSocketServiceImpl(auctionManager: ActorRef[AuctionManagerCommand])(implicit system: ActorSystem[_])
    extends BiddingWebSocketService {
  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def watchBids(auctionId: UUID, lotId: UUID): Flow[Message, Message, NotUsed] = {

    val actorSource = ActorSource
      .actorRef[Event](
        completionMatcher = { case Closed =>
          TextMessage.Strict(s"Lot closed. Bye bye!")
        },
        failureMatcher = {
          case AuctionNotFound(msg)         => throw EntityNotFoundException(msg)
          case LotNotFound(msg)             => throw EntityNotFoundException(msg)
          case ReadOnlyCommandReceived(msg) => throw IllegalAuctionStateException(msg)
        },
        bufferSize = 10,
        OverflowStrategy.dropHead
      )
      .mapMaterializedValue(ref => auctionManager ! WrappedAuctionCommand(auctionId, SubscribeLot(lotId, ref)))
      .collect {
        case Subscribed(_)             => TextMessage.Strict(s"You are subscribed to lot: $lotId")
        case BidMade(_, _, winningBid) => TextMessage.Strict(s"Winning bid changed: $winningBid")
      }

    Flow.fromSinkAndSource(Sink.ignore, actorSource)
  }
}
