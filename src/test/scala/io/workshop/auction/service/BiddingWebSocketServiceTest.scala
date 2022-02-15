package io.workshop.auction.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ ScalatestRouteTest, WSProbe }
import akka.stream.testkit.TestSubscriber
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{ JsObject, JsString, JsValue }

import java.util.UUID

class BiddingWebSocketServiceTest
    extends AnyWordSpec
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with Matchers
    with PlayJsonSupport {

  import akka.actor.typed.scaladsl.adapter._

  private lazy val testKit: ActorTestKit           = ActorTestKit()
  private implicit val typedSystem: ActorSystem[_] = system.toTyped

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val validUserToken  = OAuth2BearerToken("superSecretToken.3")
  private val auctionToCreate = JsObject(Map("name" -> JsString("Van Gogh's The Starry Night")))

  "BiddingWebSocket" must {

    "subscribe to lot" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val wsRoutes: Route  = new BiddingWebSocketServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)

      val wsClient = WSProbe()

      var auctionId = ""
      var lotId     = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      WS(s"/ws/auctions/$auctionId/lots/$lotId", wsClient.flow) ~> wsRoutes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.expectMessage(s"You are subscribed to lot: $lotId")
      }
    }

    "watch winning bid" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val wsRoutes: Route  = new BiddingWebSocketServiceImpl(auctionManager).route
      val bidRequest       = BidRequest(100, None)
      val lotDetails       = LotDetails(0, None)

      val wsClient = WSProbe()

      var auctionId = ""
      var lotId     = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      WS(s"/ws/auctions/$auctionId/lots/$lotId", wsClient.flow) ~> wsRoutes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.expectMessage(s"You are subscribed to lot: $lotId")

        Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(
          validUserToken
        ) ~> lotRoutes ~> check {
          response.status shouldEqual StatusCodes.OK

          wsClient.expectMessage(s"Winning bid changed: ${bidRequest.bid}")
        }
      }
    }

    "not allow to subscribe on closed state" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val wsRoutes: Route  = new BiddingWebSocketServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)

      val wsClient = WSProbe()

      var auctionId = ""
      var lotId     = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      WS(s"/ws/auctions/$auctionId/lots/$lotId", wsClient.flow) ~> wsRoutes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.inProbe.expectEvent()
        wsClient.inProbe.expectEvent(
          TestSubscriber.OnError(
            IllegalAuctionStateException(
              s"Could not operate subscribe lot request cause of current state is ${States.Closed}"
            )
          )
        )
      }
    }

    "not allow to subscribe on finished state" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val wsRoutes: Route  = new BiddingWebSocketServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)

      val wsClient = WSProbe()

      var auctionId = ""
      var lotId     = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      WS(s"/ws/auctions/$auctionId/lots/$lotId", wsClient.flow) ~> wsRoutes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.inProbe.expectEvent()
        wsClient.inProbe.expectEvent(
          TestSubscriber.OnError(
            IllegalAuctionStateException(
              s"Could not operate subscribe lot request cause of current state is ${States.Finished}"
            )
          )
        )
      }
    }

    "complete socket session on auction end" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val wsRoutes: Route  = new BiddingWebSocketServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)

      val wsClient = WSProbe()

      var auctionId = ""
      var lotId     = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      WS(s"/ws/auctions/$auctionId/lots/$lotId", wsClient.flow) ~> wsRoutes ~> check {
        isWebSocketUpgrade shouldEqual true
        wsClient.expectMessage(s"You are subscribed to lot: $lotId")

        Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
          response.status shouldEqual StatusCodes.OK
          wsClient.expectCompletion()
        }
      }

    }

  }
}
