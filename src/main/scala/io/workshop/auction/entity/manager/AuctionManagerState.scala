package io.workshop.auction.entity.manager

case class AuctionManagerState(id: String, auctions: Auctions = Map.empty) extends AuctionSerializable
