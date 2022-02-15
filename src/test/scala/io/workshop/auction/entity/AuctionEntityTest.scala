package io.workshop.auction.entity

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{ EventSourcedBehaviorTestKit, PersistenceTestKit }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.util.UUID

class AuctionEntityTest
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  private val persistenceTestKit = PersistenceTestKit(system)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    persistenceTestKit.clearAll()
  }

  "Auction Actor" must {
    "allow to add a lot on closed state" in {
      // setup
      val auctionId = UUID.randomUUID()
      val probe     = createTestProbe[Event]()

      val auctionActor = spawn(AuctionEntity(auctionId))

      // execute
      auctionActor ! AddLot(probe.ref, LotDetails(0, None))
      val addedLot = probe.receiveMessage().asInstanceOf[LotAdded]

      // assert
      persistenceTestKit.expectNextPersisted(auctionId.toString, addedLot)
    }

    "allow to add multiple lots on closed state" in {
      // setup
      val auctionId = UUID.randomUUID()
      val probe     = createTestProbe[Event]()

      val auctionActor = spawn(AuctionEntity(auctionId))

      // execute
      auctionActor ! AddLot(probe.ref)
      val lotAdded1 = probe.receiveMessage().asInstanceOf[LotAdded]
      auctionActor ! AddLot(probe.ref)
      val lotAdded2 = probe.receiveMessage().asInstanceOf[LotAdded]

      // assert
      persistenceTestKit.expectNextPersisted(auctionId.toString, LotAdded(lotAdded1.lotId, LotDetails(0, None)))
      persistenceTestKit.expectNextPersisted(auctionId.toString, LotAdded(lotAdded2.lotId, LotDetails(0, None)))
    }

    "allow to de-register a lot on closed state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      val lotAdded = probe.expectMessageType[LotAdded]

      // execute
      auctionActor ! RemoveLot(lotAdded.lotId, probe.ref)

      // assert
      probe.expectMessage(LotRemoved(lotAdded.lotId))
      persistenceTestKit.expectNextPersisted(auctionId.toString, LotAdded(lotAdded.lotId, LotDetails(0, None)))
      persistenceTestKit.expectNextPersisted(auctionId.toString, LotRemoved(lotAdded.lotId))
    }

    "allow to de-register a specific lot on closed state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      val lotAdded = probe.expectMessageType[LotAdded]

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)

      probe.receiveMessages(5)
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 6)

      // execute
      auctionActor ! RemoveLot(lotAdded.lotId, probe.ref)

      // assert
      probe.expectMessage(LotRemoved(lotAdded.lotId))
      persistenceTestKit.expectNextPersisted(auctionId.toString, LotRemoved(lotAdded.lotId))
    }

    "not allow to add a lot on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(2)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 2)

      // execute
      auctionActor ! AddLot(probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate adding lot request cause of current state is ${States.InProgress}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to add a lot on finished state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      auctionActor ! EndAuction(probe.ref)
      probe.receiveMessages(3)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 3)

      // execute
      auctionActor ! AddLot(probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate adding lot request cause of current state is ${States.Finished}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to de-register a lot on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val lotId        = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(2)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 2)

      // execute
      auctionActor ! RemoveLot(lotId, probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(
          s"Could not operate removing lot request cause of current state is ${States.InProgress}"
        )
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to de-register a lot on finished state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      val lotAdded = probe.expectMessageType[LotAdded]
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 1)

      auctionActor ! StartAuction(probe.ref)
      auctionActor ! EndAuction(probe.ref)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 2)
      probe.receiveMessages(2)

      // execute
      auctionActor ! RemoveLot(lotAdded.lotId, probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate removing lot request cause of current state is ${States.Finished}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "allow to end auction on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(2)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 2)

      // execute
      auctionActor ! EndAuction(probe.ref)

      // assert
      probe.expectMessage(AuctionEnded)
      persistenceTestKit.expectNextPersisted(auctionId.toString, AuctionEnded)
    }

    "not allow to end auction that could not be started" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      // execute
      auctionActor ! EndAuction(probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate ending auction request cause of current state is ${States.Closed}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "allow to place bid on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val bid          = Bid(UUID.randomUUID(), 100, None)

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      val addedLot = probe.receiveMessages(3).head.asInstanceOf[LotAdded].lotId
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 3)

      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessage()
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 1)

      // execute
      auctionActor ! PlaceBid(addedLot, bid, probe.ref)

      // assert
      probe.expectMessage(BidMade(addedLot, bid, 100))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "allow to place multiple bids to the same lot on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val bid          = Bid(UUID.randomUUID(), 100, None)
      val higherBid    = Bid(UUID.randomUUID(), 200, None)

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      val addedLot = probe.receiveMessages(3)(1).asInstanceOf[LotAdded].lotId
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 3)

      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessage()
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 1)

      auctionActor ! PlaceBid(addedLot, bid, probe.ref)
      probe.receiveMessage()

      // execute
      auctionActor ! PlaceBid(addedLot, higherBid, probe.ref)

      // assert
      probe.expectMessage(BidMade(addedLot, higherBid, 200))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "not allow to place bid on closed state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val bid          = Bid(UUID.randomUUID(), 100, None)

      auctionActor ! AddLot(probe.ref)
      val lotAdded = probe.expectMessageType[LotAdded]
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 1)

      // execute
      auctionActor ! PlaceBid(lotAdded.lotId, bid, probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate placing bid request cause of current state is ${States.Closed}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to place bid on finished state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val bid          = Bid(UUID.randomUUID(), 100, None)

      auctionActor ! AddLot(probe.ref)
      val lotAdded = probe.expectMessageType[LotAdded]
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 1)

      auctionActor ! StartAuction(probe.ref)
      auctionActor ! EndAuction(probe.ref)
      probe.receiveMessages(2)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 2)

      // execute
      auctionActor ! PlaceBid(lotAdded.lotId, bid, probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate placing bid request cause of current state is ${States.Finished}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to get lot on closed state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      val lot   = probe.receiveMessages(1)
      val lotId = lot.head.asInstanceOf[LotAdded].lotId
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 1)

      // execute
      auctionActor ! GetLot(lotId, probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate get lot request cause of current state is ${States.Closed}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "allow to get lot on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      val bid_1 = Bid(UUID.randomUUID(), 100, Some(120))
      val bid_2 = Bid(UUID.randomUUID(), 130, None)
      val bid_3 = Bid(UUID.randomUUID(), 100, None)
      val bid_4 = Bid(UUID.randomUUID(), 101, Some(110))
      val bid_5 = Bid(UUID.randomUUID(), 100, None)

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      val lots   = probe.receiveMessages(3)
      val lotsId = lots.map(l => l.asInstanceOf[LotAdded].lotId)
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 3)

      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessage()
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 1)

      auctionActor ! PlaceBid(lotsId.head, bid_1, probe.ref)
      auctionActor ! PlaceBid(lotsId.head, bid_2, probe.ref)
      auctionActor ! PlaceBid(lotsId(1), bid_3, probe.ref)
      auctionActor ! PlaceBid(lotsId(1), bid_4, probe.ref)
      auctionActor ! PlaceBid(lotsId.last, bid_5, probe.ref)
      probe.receiveMessages(5)

      // execute
      auctionActor ! GetLot(lotsId(1), probe.ref)
      probe.expectMessage(LotSummaryGotten(lotsId(1), 2, Some(bid_4)))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "allow to get all lots of auction on inProgress state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      val bid_1 = Bid(UUID.randomUUID(), 100, Some(120))
      val bid_2 = Bid(UUID.randomUUID(), 130, None)
      val bid_3 = Bid(UUID.randomUUID(), 100, None)
      val bid_4 = Bid(UUID.randomUUID(), 101, Some(110))
      val bid_5 = Bid(UUID.randomUUID(), 100, None)

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      val lots   = probe.receiveMessages(3)
      val lotsId = lots.map(l => l.asInstanceOf[LotAdded].lotId)
      persistenceTestKit.receivePersisted[LotAdded](auctionId.toString, 3)

      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessage()
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 1)

      auctionActor ! PlaceBid(lotsId.head, bid_1, probe.ref)
      probe.receiveMessage()

      auctionActor ! PlaceBid(lotsId(1), bid_3, probe.ref)
      probe.receiveMessage()

      auctionActor ! PlaceBid(lotsId.last, bid_4, probe.ref)
      probe.receiveMessage()

      auctionActor ! PlaceBid(lotsId.head, bid_2, probe.ref)
      probe.receiveMessage()

      auctionActor ! PlaceBid(lotsId.last, bid_5, probe.ref)
      probe.receiveMessage()

      // execute
      auctionActor ! GetAllLots(probe.ref)
      val receivedLots = probe.expectMessageType[LotsReceived]

      receivedLots.lots should have size 3
      receivedLots.lots should contain(LotSummaryGotten(lotsId.head, 2, Some(bid_2)))
      receivedLots.lots should contain(LotSummaryGotten(lotsId(1), 1, Some(bid_3)))
      receivedLots.lots should contain(LotSummaryGotten(lotsId.last, 1, Some(bid_4)))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "not allow to get all lots of auction on closed state" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      // execute
      auctionActor ! GetAllLots(probe.ref)

      // assert
      probe.expectMessage(
        ReadOnlyCommandReceived(s"Could not operate get all lots request cause of current state is ${States.Closed}")
      )
      persistenceTestKit.rejectNextRead(auctionId.toString)
    }

    "not allow to bid on lot that not exists" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val randomLotId  = UUID.randomUUID()
      val bid          = Bid(UUID.randomUUID(), 100, Some(120))

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(4)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 4)

      // execute
      auctionActor ! PlaceBid(randomLotId, bid, probe.ref)

      // assert
      probe.expectMessage(LotNotFound(s"Could not find lot by id $randomLotId"))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "not allow to get lot that not exists" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()
      val randomLotId  = UUID.randomUUID()

      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! AddLot(probe.ref)
      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(4)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 4)

      // execute
      auctionActor ! GetLot(randomLotId, probe.ref)

      // assert
      probe.expectMessage(LotNotFound(s"Could not find lot by id $randomLotId"))
      persistenceTestKit.expectNothingPersisted(auctionId.toString)
    }

    "allow to close lots on ending auction" in {
      // setup
      val auctionId    = UUID.randomUUID()
      val auctionActor = spawn(AuctionEntity(auctionId))
      val probe        = createTestProbe[Event]()

      auctionActor ! AddLot(probe.ref)
      val lotAdded1 = probe.receiveMessage().asInstanceOf[LotAdded]
      auctionActor ! AddLot(probe.ref)
      val lotAdded2 = probe.receiveMessage().asInstanceOf[LotAdded]
      auctionActor ! AddLot(probe.ref)
      val lotAdded3 = probe.receiveMessage().asInstanceOf[LotAdded]

      auctionActor ! StartAuction(probe.ref)
      probe.receiveMessages(1)
      persistenceTestKit.receivePersisted[AuctionEvent](auctionId.toString, 4)

      auctionActor ! SubscribeLot(lotAdded1.lotId, probe.ref)
      auctionActor ! SubscribeLot(lotAdded2.lotId, probe.ref)
      auctionActor ! SubscribeLot(lotAdded3.lotId, probe.ref)
      probe.receiveMessages(3)

      auctionActor ! EndAuction(probe.ref)
      val messages = probe.receiveMessages(4)

      messages should contain(AuctionEnded)
      messages should contain(Closed)
      persistenceTestKit.expectNextPersisted(auctionId.toString, AuctionEnded)
    }
  }
}
