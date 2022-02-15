package io.workshop.auction.protocol.command

import akka.actor.typed.ActorRef

import java.util.UUID

sealed trait AuctionManagerCommand extends Command

final case class CreateAuction(name: String, replyTo: ActorRef[AuctionManagerEvent]) extends AuctionManagerCommand

final case class ListAuctions(replyTo: ActorRef[Event]) extends AuctionManagerCommand

final case class WrappedAuctionCommand(auctionId: UUID, command: AuctionCommand) extends AuctionManagerCommand
