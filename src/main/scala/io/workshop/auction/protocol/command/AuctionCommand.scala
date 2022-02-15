package io.workshop.auction.protocol.command

import akka.actor.typed.ActorRef

import java.util.UUID

sealed trait AuctionCommand extends Command {
  val replyTo: ActorRef[AuctionEvent]
}

final case class AddLot(replyTo: ActorRef[AuctionEvent], details: LotDetails = LotDetails(0, None))
    extends AuctionCommand

final case class RemoveLot(lotId: UUID, replyTo: ActorRef[Event]) extends AuctionCommand

final case class GetLot(lotId: UUID, replyTo: ActorRef[Event]) extends AuctionCommand

final case class GetAllLots(replyTo: ActorRef[Event]) extends AuctionCommand

final case class PlaceBid(lotId: UUID, bid: Bid, replyTo: ActorRef[Event]) extends AuctionCommand

final case class StartAuction(replyTo: ActorRef[AuctionEvent]) extends AuctionCommand

final case class EndAuction(replyTo: ActorRef[Event]) extends AuctionCommand

final case class SubscribeLot(lotId: UUID, replyTo: ActorRef[Event]) extends AuctionCommand

final case class GetAuction(name: String, replyTo: ActorRef[Event]) extends AuctionCommand
