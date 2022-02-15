package io.workshop.auction.protocol.command

import akka.actor.typed.ActorRef
import io.workshop.auction.protocol.event.Event

import java.util.UUID

sealed trait LotCommand extends Command {
  val id: UUID
}

final case class MakeBid(id: UUID, bid: Bid, replyTo: ActorRef[Event]) extends LotCommand

final case class GetLotSummary(id: UUID, replyTo: ActorRef[Event]) extends LotCommand

final case class Subscribe(id: UUID, replyTo: ActorRef[Event]) extends LotCommand

final case class Close(id: UUID, replyTo: ActorRef[Event]) extends LotCommand
