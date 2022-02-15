package io.workshop.auction.service.auction

import play.api.libs.json.{ Format, Json }

import java.util.UUID

sealed trait AuctionResponses

final case class AuctionIdResponse(id: UUID) extends AuctionResponses

object AuctionIdResponse {
  implicit val format: Format[AuctionIdResponse] = Json.format
}

final case class AuctionResponse(id: UUID, name: String, status: States) extends AuctionResponses

object AuctionResponse {
  implicit val format: Format[AuctionResponse] = Json.format
}
