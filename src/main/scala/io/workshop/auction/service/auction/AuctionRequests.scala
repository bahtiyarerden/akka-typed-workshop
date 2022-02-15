package io.workshop.auction.service.auction

import play.api.libs.json.{ Format, Json }

sealed trait AuctionRequests

case class CreateAuctionRequest(name: String) extends AuctionRequests

object CreateAuctionRequest {
  implicit val format: Format[CreateAuctionRequest] = Json.format
}
