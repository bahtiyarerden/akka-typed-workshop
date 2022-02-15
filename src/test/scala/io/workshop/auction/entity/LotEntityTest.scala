package io.workshop.auction.entity

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{ EventSourcedBehaviorTestKit, PersistenceTestKit }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.util.UUID

class LotEntityTest
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

  "LotActor" must {
    "accept the current bid as a highest bid on empty bid list" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))
      val bid         = Bid(userId, bid = 10, None)

      // execute
      lotActorRef ! MakeBid(lotId, bid, probe.ref)

      // assert
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.bid))
      probe.expectMessage(BidMade(lotId, bid, bid.bid))
    }

    "handle a higher bid than the previous highest bid " in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))
      val bid         = Bid(userId, bid = 10, None)
      val higherBid   = Bid(userId, bid = 20, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.bid))
      probe.expectMessage(BidMade(lotId, bid, bid.bid))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid, higherBid.bid))
      probe.expectMessage(BidMade(lotId, higherBid, higherBid.bid))
    }

    "handle the user's maximum bid as a winning bid" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))
      val bid         = Bid(userId, bid = 10, Some(19))

      // execute
      lotActorRef ! MakeBid(lotId, bid, probe.ref)

      // assert
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
    }

    "accept a bid higher than the maximum bid set by the user" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))
      val bid         = Bid(userId, bid = 10, Some(19))
      val higherBid   = Bid(userId, bid = 20, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))

      // execute
      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)

      // assert
      probe.expectMessage(BidMade(lotId, higherBid, higherBid.bid))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid, higherBid.bid))

    }

    "accept the user's lowest possible bid as a winning bid" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))
      val bid         = Bid(userId, bid = 10, Some(19))
      val higherBid   = Bid(userId, bid = 18, Some(40))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))

      // execute
      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)

      // assert
      probe.expectMessage(BidMade(lotId, higherBid.copy(bid = 20), higherBid.maxBid.head))
      persistenceTestKit.expectNextPersisted(
        lotId.toString,
        BidMade(lotId, higherBid.copy(bid = 20), higherBid.maxBid.head)
      )
    }

    "not allow if the bid does not exceed the maximum bid" in {
      // setup
      val lotId             = UUID.randomUUID()
      val userId            = UUID.randomUUID()
      val probe             = createTestProbe[Event]()
      val lotActorRef       = spawn(LotEntity(lotId))
      val bid               = Bid(userId, bid = 10, None)
      val possibleHigherBid = Bid(userId, bid = 8, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.bid))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.bid))

      // execute
      lotActorRef ! MakeBid(lotId, possibleHigherBid, probe.ref)

      // assert
      probe.expectMessage(BidNotMade(s"Can not bidding to lot: $lotId its maximum bid is ${bid.bid}"))
      persistenceTestKit.expectNothingPersisted(lotId.toString)
    }

    "not allow if the users maximum bid does not exceed the current maximum bid" in {
      // setup
      val lotId             = UUID.randomUUID()
      val userId            = UUID.randomUUID()
      val probe             = createTestProbe[Event]()
      val lotActorRef       = spawn(LotEntity(lotId))
      val bid               = Bid(userId, bid = 10, Some(19))
      val possibleHigherBid = Bid(userId, bid = 18, Some(19))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))

      // execute
      lotActorRef ! MakeBid(lotId, possibleHigherBid, probe.ref)

      // assert
      probe.expectMessage(BidNotMade(s"Can not bidding to lot: $lotId its maximum bid is ${bid.maxBid.head}"))
      persistenceTestKit.expectNothingPersisted(lotId.toString)
    }

    "allow to subscribe to lot" in {
      // setup
      val lotId       = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))

      // execute
      lotActorRef ! Subscribe(lotId, probe.ref)

      // assert
      probe.expectMessage(Subscribed(probe.ref))
      persistenceTestKit.expectNextPersisted(lotId.toString, Subscribed(probe.ref))
    }

    "allow to close lot" in {
      // setup
      val lotId       = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))

      // execute
      lotActorRef ! Close(lotId, probe.ref)

      // assert
      probe.expectNoMessage()
      persistenceTestKit.expectNextPersisted(lotId.toString, Closed)
    }

    "allow to close subscribed lot" in {
      // setup
      val lotId       = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val lotActorRef = spawn(LotEntity(lotId))

      lotActorRef ! Subscribe(lotId, probe.ref)
      probe.receiveMessage()
      persistenceTestKit.expectNextPersisted(lotId.toString, Subscribed(probe.ref))

      // execute
      lotActorRef ! Close(lotId, probe.ref)

      // assert
      probe.expectMessage(Closed)
      persistenceTestKit.expectNextPersisted(lotId.toString, Closed)
    }

    "allow to make bid lower than maximum price" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(25))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))

      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))
    }

    "allow to make bid higher than maximum price" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(40))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid.copy(bid = maxPrice), maxPrice))

      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid.copy(bid = maxPrice), maxPrice))
    }

    "allow to make multiple bids lower than maximum price" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(25))
      val higherBid   = Bid(userId, bid = 30, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(BidMade(lotId, higherBid, maxPrice))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid, maxPrice))
    }

    "not allow to make multiple bids lower than maximum price" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(30))
      val higherBid   = Bid(userId, bid = 35, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, bid.maxBid.head))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, bid.maxBid.head))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(
        BidNotMade(s"Can not bidding to lot: $lotId. The maximum bid has been reached. Thanks for joining")
      )
    }

    "allow to make multiple bids while max bid not set but max price set" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, None)
      val higherBid   = Bid(userId, bid = 25, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, 18))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, 18))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(BidMade(lotId, higherBid, 25))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid, 25))
    }

    "allow to make multiple bids with higher than max price while max bid not set" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, None)
      val higherBid   = Bid(userId, bid = 35, None)

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, 18))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, 18))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(BidMade(lotId, higherBid.copy(bid = 30), 30))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid.copy(bid = 30), 30))
    }

    "allow to make multiple bids while max bid and max price set" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(20))
      val higherBid   = Bid(userId, bid = 19, Some(25))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, 20))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, 20))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(BidMade(lotId, higherBid.copy(bid = 21), 25))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid.copy(bid = 21), 25))
    }

    "allow to make multiple bids while max bid and max price set and higher then max price" in {
      // setup
      val lotId       = UUID.randomUUID()
      val userId      = UUID.randomUUID()
      val probe       = createTestProbe[Event]()
      val maxPrice    = 30
      val lotActorRef = spawn(LotEntity(lotId, 0, Some(maxPrice)))
      val bid         = Bid(userId, bid = 18, Some(20))
      val higherBid   = Bid(userId, bid = 19, Some(35))

      lotActorRef ! MakeBid(lotId, bid, probe.ref)
      probe.expectMessage(BidMade(lotId, bid, 20))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, bid, 20))

      lotActorRef ! MakeBid(lotId, higherBid, probe.ref)
      probe.expectMessage(BidMade(lotId, higherBid.copy(bid = 30), 30))
      persistenceTestKit.expectNextPersisted(lotId.toString, BidMade(lotId, higherBid.copy(bid = 30), 30))
    }
  }
}
