package io.workshop.auction.protocol.event

import java.util.UUID

sealed trait AuctionManagerEvent extends Event

sealed trait AuctionManagerError extends AuctionManagerEvent

final case class AuctionCreated(name: String, auctionId: UUID) extends AuctionManagerEvent

final case class AuctionsReceived(auctions: Seq[GotAuction]) extends AuctionManagerEvent

final case class AuctionsNotReceivedException(
  private val message: String  = "",
  private val cause: Throwable = None.orNull
) extends Exception(message, cause)
    with AuctionManagerError
