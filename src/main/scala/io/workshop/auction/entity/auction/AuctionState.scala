package io.workshop.auction.entity.auction

import play.api.libs.json.Format

import java.util.UUID

object States extends Enumeration {
  type States = Value
  val Closed, InProgress, Finished = Value

  implicit val format: Format[States] = JsonFormats.enumFormat(this)
}

sealed trait AuctionState extends AuctionSerializable {
  val id: UUID
  val state: States.Value
  val lots: Lots
}

final case class ClosedState(
  override val id: UUID,
  override val lots: Lots          = Map.empty,
  override val state: States.Value = States.Closed
) extends AuctionState

final case class InProgressState(
  override val id: UUID,
  override val lots: Lots,
  override val state: States.Value = States.InProgress
) extends AuctionState

final case class FinishedState(
  override val id: UUID,
  override val lots: Lots,
  override val state: States.Value = States.Finished
) extends AuctionState
