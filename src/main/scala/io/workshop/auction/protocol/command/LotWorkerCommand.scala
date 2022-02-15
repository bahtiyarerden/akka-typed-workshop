package io.workshop.auction.protocol.command

import akka.actor.typed.ActorRef

import java.util.UUID

sealed trait LotWorkerCommand extends Command

final case class OfferBid(lotId: UUID, bid: Bid, lot: ActorRef[LotCommand], replyTo: ActorRef[Event])
    extends LotWorkerCommand

final case class CollectLotSummary(lotId: UUID, lot: ActorRef[LotCommand], replyTo: ActorRef[Event])
    extends LotWorkerCommand

final case class CollectAllLotsSummary(lots: Map[UUID, ActorRef[LotCommand]], replyTo: ActorRef[Event])
    extends LotWorkerCommand

final case class ForwardSubscriber(lotId: UUID, lot: ActorRef[LotCommand], replyTo: ActorRef[Event])
    extends LotWorkerCommand

final case class CloseLots(lots: Map[UUID, ActorRef[LotCommand]], replyTo: ActorRef[Event]) extends LotWorkerCommand
