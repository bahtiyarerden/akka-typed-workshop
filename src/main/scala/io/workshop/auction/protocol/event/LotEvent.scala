package io.workshop.auction.protocol.event

import akka.actor.typed.ActorRef

import java.util.UUID

sealed trait LotEvent extends Event

final case class BidMade(lotId: UUID, bid: Bid, winningBid: Int) extends LotEvent

final case class LotSummaryGotten(lotId: UUID, totalBidCount: Int, bid: Option[Bid]) extends LotEvent

final case class BidNotMade(message: String) extends LotEvent

final case class Subscribed(subscriber: ActorRef[Event]) extends LotEvent

case object Closed extends LotEvent
