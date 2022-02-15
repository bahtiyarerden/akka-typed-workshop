package io.workshop.auction.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class LotWorkerActorTest
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers {

  "LotWorkerActor" must {
    "offer bid on lot" in {
      // setup
      val lotId     = UUID.randomUUID()
      val lotRef    = spawn(LotEntity(lotId))
      val workerRef = spawn(LotWorkerActor())
      val bid       = Bid(UUID.randomUUID(), 100, None)
      val probe     = createTestProbe[Event]()

      // execute
      workerRef ! OfferBid(lotId, bid, lotRef, probe.ref)

      // assert
      probe.expectMessage(BidMade(lotId, bid, 100))
    }

    "offer lower bid on lot" in {
      // setup
      val lotId      = UUID.randomUUID()
      val lotRef     = spawn(LotEntity(lotId))
      val workerRef  = spawn(LotWorkerActor())
      val winningBid = 120
      val bid        = Bid(UUID.randomUUID(), 100, Some(winningBid))
      val lowerBid   = Bid(UUID.randomUUID(), 110, None)

      val probe = createTestProbe[Event]()

      workerRef ! OfferBid(lotId, bid, lotRef, probe.ref)
      probe.receiveMessage()

      // execute
      workerRef ! OfferBid(lotId, lowerBid, lotRef, probe.ref)

      // assert
      probe.expectMessage(BidNotMade(s"Can not bidding to lot: $lotId its maximum bid is $winningBid"))
    }

    "collect summary of lot" in {
      // setup
      val lotId     = UUID.randomUUID()
      val lotRef    = spawn(LotEntity(lotId))
      val workerRef = spawn(LotWorkerActor())
      val bid       = Bid(UUID.randomUUID(), 100, Some(120))

      val probe = createTestProbe[Event]()

      workerRef ! OfferBid(lotId, bid, lotRef, probe.ref)
      probe.receiveMessage()

      // execute
      workerRef ! CollectLotSummary(lotId, lotRef, probe.ref)

      // assert
      probe.expectMessage(LotSummaryGotten(lotId, 1, Some(bid)))
    }

    "collect summaries of multiple lots" in {
      // setup
      val lotId1                                  = UUID.randomUUID()
      val lotId2                                  = UUID.randomUUID()
      val lotRef1                                 = spawn(LotEntity(lotId1))
      val lotRef2                                 = spawn(LotEntity(lotId2))
      val lotMap: Map[UUID, ActorRef[LotCommand]] = Map(lotId1 -> lotRef1, lotId2 -> lotRef2)
      val workerRef                               = spawn(LotWorkerActor())
      val bid1                                    = Bid(UUID.randomUUID(), 90, Some(100))
      val bid2                                    = Bid(UUID.randomUUID(), 110, Some(120))
      val bid3                                    = Bid(UUID.randomUUID(), 90, Some(100))
      val bid4                                    = Bid(UUID.randomUUID(), 110, Some(120))

      val probe = createTestProbe[Event]()

      workerRef ! OfferBid(lotId1, bid1, lotRef1, probe.ref)
      workerRef ! OfferBid(lotId1, bid2, lotRef1, probe.ref)
      workerRef ! OfferBid(lotId2, bid3, lotRef2, probe.ref)
      workerRef ! OfferBid(lotId2, bid4, lotRef2, probe.ref)

      probe.receiveMessages(4)

      // execute
      workerRef ! CollectAllLotsSummary(lotMap, probe.ref)

      // assert
      val lotsReceived = probe.expectMessageType[LotsReceived]
      lotsReceived.lots should have size 2
      lotsReceived.lots should contain(LotSummaryGotten(lotId1, 2, Some(bid2)))
      lotsReceived.lots should contain(LotSummaryGotten(lotId2, 2, Some(bid4)))
    }

    "allow to subscribe to lot" in {
      // setup
      val lotId     = UUID.randomUUID()
      val lotRef    = spawn(LotEntity(lotId))
      val workerRef = spawn(LotWorkerActor())
      val probe     = createTestProbe[Event]()

      // execute
      workerRef ! ForwardSubscriber(lotId, lotRef, probe.ref)

      // assert
      probe.expectMessage(Subscribed(probe.ref))
    }

    "allow to close lots" in {
      // setup
      val lotId                                   = UUID.randomUUID()
      val lotId2                                  = UUID.randomUUID()
      val lotRef                                  = spawn(LotEntity(lotId))
      val lotRef2                                 = spawn(LotEntity(lotId))
      val workerRef                               = spawn(LotWorkerActor())
      val probe                                   = createTestProbe[Event]()
      val lotMap: Map[UUID, ActorRef[LotCommand]] = Map(lotId -> lotRef, lotId2 -> lotRef2)

      // execute
      workerRef ! CloseLots(lotMap, probe.ref)

      // assert
      probe.expectNoMessage()
    }

    "allow to notify subscribers to lot closed" in {
      // setup
      val lotId                                   = UUID.randomUUID()
      val lotRef                                  = spawn(LotEntity(lotId))
      val workerRef                               = spawn(LotWorkerActor())
      val probe                                   = createTestProbe[Event]()
      val lotMap: Map[UUID, ActorRef[LotCommand]] = Map(lotId -> lotRef)

      workerRef ! ForwardSubscriber(lotId, lotRef, probe.ref)
      probe.receiveMessage()

      // execute
      workerRef ! CloseLots(lotMap, probe.ref)

      // assert
      probe.expectMessage(Closed)
    }
  }
}
