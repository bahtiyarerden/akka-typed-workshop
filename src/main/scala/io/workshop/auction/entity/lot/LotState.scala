package io.workshop.auction.entity.lot

import akka.actor.typed.ActorRef

import java.util.UUID

final case class LotState(
  id: UUID,
  bids: List[Bid]                    = List.empty,
  subscribers: List[ActorRef[Event]] = List.empty,
  initialPrice: Int                  = 0,
  maxPrice: Option[Int]              = None,
  winningBid: Int                    = 0
) extends AuctionSerializable
