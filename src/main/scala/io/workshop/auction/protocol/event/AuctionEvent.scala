package io.workshop.auction.protocol.event

import java.util.UUID

sealed trait AuctionEvent extends Event

sealed trait AuctionError extends AuctionEvent

case object AuctionStarted extends AuctionEvent

case object AuctionEnded extends AuctionEvent

final case class LotRemoved(lotId: UUID) extends AuctionEvent

final case class LotAdded(lotId: UUID, details: LotDetails) extends AuctionEvent

final case class LotsReceived(lots: Seq[LotSummaryGotten]) extends AuctionEvent

final case class GotAuction(id: UUID, name: String, status: Value) extends AuctionEvent

final case class ReadOnlyCommandReceived(msg: String) extends AuctionError

final case class AuctionNotFound(message: String) extends AuctionError

final case class LotNotFound(message: String) extends AuctionError

final case class AuctionNotStarted(message: String) extends AuctionError

final case class LotsNotReceivedException(private val message: String = "", private val cause: Option[Throwable] = None)
    extends RuntimeException(message)
    with AuctionError {
  cause.foreach(initCause)
}
