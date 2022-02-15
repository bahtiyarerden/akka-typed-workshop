package io.workshop.auction.service.lot

import play.api.libs.json.{ Format, Json }

sealed trait LotRequests

final case class BidRequest(bid: Int, maxBid: Option[Int]) extends LotRequests

object BidRequest {
  implicit val format: Format[BidRequest] = Json.format
}

final case class CreateLotRequest(initialPrice: Int, maxPrice: Option[Int])

object CreateLotRequest {
  implicit val format: Format[CreateLotRequest] = Json.format
}
