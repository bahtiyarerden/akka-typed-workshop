package io.workshop.auction.entity

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{ EventSourcedBehaviorTestKit, PersistenceTestKit }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.util.UUID

class AuctionManagerEntityTest
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

  "AuctionManagerActor" must {
    "allow to create new auction" in {
      // setup
      val managerId      = UUID.randomUUID()
      val auctionManager = spawn(AuctionManagerEntity(managerId.toString))
      val probe          = createTestProbe[Event]()
      val auctionName    = "Van Gogh's The Starry Night"

      // execute
      auctionManager ! CreateAuction(auctionName, probe.ref)
      val auctionCreated = probe.receiveMessage().asInstanceOf[AuctionCreated]

      // assert
      persistenceTestKit.expectNextPersisted(managerId.toString, AuctionCreated(auctionName, auctionCreated.auctionId))
    }

    "allow to list auctions" in {
      // setup
      val managerId      = UUID.randomUUID()
      val auctionManager = spawn(AuctionManagerEntity(managerId.toString))
      val probe          = createTestProbe[Event]()

      auctionManager ! CreateAuction("Van Gogh's The Starry Night", probe.ref)
      val auction1Created = probe.expectMessageType[AuctionCreated]
      auctionManager ! CreateAuction("Pablo Picasso's Guernica", probe.ref)
      val auction2Created = probe.expectMessageType[AuctionCreated]
      auctionManager ! CreateAuction("Turner's The Slave Ship", probe.ref)
      val auction3Created = probe.expectMessageType[AuctionCreated]

      persistenceTestKit.receivePersisted[AuctionCreated](managerId.toString, 3)

      // execute
      auctionManager ! ListAuctions(probe.ref)

      // assert
      val auctionsListed = probe.expectMessageType[AuctionsReceived]
      auctionsListed.auctions should have size 3
      persistenceTestKit.expectNothingPersisted(managerId.toString)
    }

    "allow to bid multiple auction" in {
      // setup
      val managerId      = UUID.randomUUID()
      val auctionManager = spawn(AuctionManagerEntity(managerId.toString))
      val probe          = createTestProbe[Event]()
      val bid1           = Bid(UUID.randomUUID(), 100, None)
      val bid2           = Bid(UUID.randomUUID(), 90, None)

      auctionManager ! CreateAuction("Van Gogh's The Starry Night", probe.ref)
      val auctionCreated = probe.expectMessageType[AuctionCreated]
      auctionManager ! CreateAuction("Pablo Picasso's Guernica", probe.ref)
      val auction2Created = probe.expectMessageType[AuctionCreated]
      persistenceTestKit.receivePersisted[AuctionCreated](managerId.toString, 2)

      auctionManager ! WrappedAuctionCommand(auctionCreated.auctionId, AddLot(probe.ref))
      val lotAdded = probe.expectMessageType[LotAdded]
      auctionManager ! WrappedAuctionCommand(auction2Created.auctionId, AddLot(probe.ref))
      val lot2Added = probe.expectMessageType[LotAdded]

      auctionManager ! WrappedAuctionCommand(auctionCreated.auctionId, StartAuction(probe.ref))
      auctionManager ! WrappedAuctionCommand(auction2Created.auctionId, StartAuction(probe.ref))
      probe.receiveMessages(2)

      // execute
      auctionManager ! WrappedAuctionCommand(auctionCreated.auctionId, PlaceBid(lotAdded.lotId, bid1, probe.ref))
      val bid1Placed = probe.expectMessageType[BidMade]
      auctionManager ! WrappedAuctionCommand(auction2Created.auctionId, PlaceBid(lot2Added.lotId, bid2, probe.ref))
      val bid2Placed = probe.expectMessageType[BidMade]

      // assert
      bid1Placed shouldEqual BidMade(lotAdded.lotId, bid1, 100)
      bid2Placed shouldEqual BidMade(lot2Added.lotId, bid2, 90)
      persistenceTestKit.expectNothingPersisted(managerId.toString)
    }

    "send auction to wrapped command" in {
      // setup
      val managerId      = UUID.randomUUID()
      val auctionManager = spawn(AuctionManagerEntity(managerId.toString))
      val probe          = createTestProbe[Event]()

      auctionManager ! CreateAuction("Van Gogh's The Starry Night", probe.ref)
      val auctionCreated = probe.expectMessageType[AuctionCreated]
      persistenceTestKit.receivePersisted[AuctionCreated](managerId.toString, 1)

      // execute
      auctionManager ! WrappedAuctionCommand(auctionCreated.auctionId, AddLot(probe.ref))

      // assert
      probe.expectMessageType[LotAdded]
      persistenceTestKit.expectNothingPersisted(managerId.toString)
    }

    "not send wrap command if auction is not found" in {
      // setup
      val managerId       = UUID.randomUUID()
      val auctionManager  = spawn(AuctionManagerEntity(managerId.toString))
      val probe           = createTestProbe[Event]()
      val randomAuctionId = UUID.randomUUID()

      auctionManager ! CreateAuction("Van Gogh's The Starry Night", probe.ref)
      probe.receiveMessage()
      persistenceTestKit.receivePersisted[AuctionCreated](managerId.toString, 1)

      // execute
      auctionManager ! WrappedAuctionCommand(randomAuctionId, AddLot(probe.ref))

      // assert
      probe.expectMessage(AuctionNotFound(s"Could not find auction by id $randomAuctionId"))
      persistenceTestKit.expectNothingPersisted(managerId.toString)
    }
  }
}
