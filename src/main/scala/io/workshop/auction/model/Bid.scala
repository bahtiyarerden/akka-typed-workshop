package io.workshop.auction.model

import play.api.libs.json.{ Format, Json }

import java.util.UUID

final case class Bid(userId: UUID, bid: Int, maxBid: Option[Int])

object Bid {
  implicit val format: Format[Bid] = Json.format
}
