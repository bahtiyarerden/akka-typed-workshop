package io.workshop.auction.service.ws

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import java.util.UUID

trait BiddingWebSocketService extends PlayJsonSupport {

  def watchBids(auctionId: UUID, lotId: UUID): Flow[Message, Message, NotUsed]

  val route: Route = pathPrefix("ws" / "auctions" / JavaUUID) { auctionId =>
    path("lots" / JavaUUID) { lotId =>
      handleWebSocketMessages(watchBids(auctionId, lotId))
    }
  }
}
