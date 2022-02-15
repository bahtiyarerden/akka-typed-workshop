package io.workshop.auction.service.lot

import play.api.libs.json.{ Format, Json }

import java.util.UUID

sealed trait LotResponses

final case class LotIdResponse(id: UUID) extends LotResponses

object LotIdResponse {
  implicit val format: Format[LotIdResponse] = Json.format
}

final case class LotResponse(auctionId: UUID, lotId: UUID, totalBidCount: Int, winningBid: Option[Bid])
    extends LotResponses

object LotResponse {
  implicit val format: Format[LotResponse] = Json.format
}
