package io.workshop.auction.model

import play.api.libs.json.{ Format, Json }

final case class LotDetails(initialPrice: Int, maxPrice: Option[Int])
object LotDetails {
  implicit val format: Format[LotDetails] = Json.format
}
