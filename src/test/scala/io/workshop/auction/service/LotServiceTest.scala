package io.workshop.auction.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import io.workshop.auction.service.lot.LotResponse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{ JsArray, JsObject, JsString, JsValue }

import java.util.UUID

class LotServiceTest
    extends AnyWordSpec
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with Matchers
    with PlayJsonSupport {

  import akka.actor.typed.scaladsl.adapter._

  private lazy val testKit: ActorTestKit           = ActorTestKit()
  private implicit val typedSystem: ActorSystem[_] = system.toTyped

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val auctionToCreate = JsObject(Map("name" -> JsString("Pablo Picasso's Guernica")))
  private val validUserToken  = OAuth2BearerToken("superSecretToken.3")

  "LotsService" must {

    "handle create lot on auction request" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
    }

    "handle failure of create lot on inProgress state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle failure of create lot on finished state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle lot not found error" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      val unknownId            = UUID.randomUUID()
      var auctionId            = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Get(s"/api/auctions/$auctionId/lots/$unknownId") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.NotFound
      }
    }

    "handle making bid to lot on inProgress state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
    }

    "handle lower bid status" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lowerBidRequest      = BidRequest(90, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", lowerBidRequest) ~> addCredentials(
        validUserToken
      ) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.Forbidden
      }
    }

    "handle failure of biding lot on closed state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle failure of biding lot on finished state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle get lot by lotId request on inProgress state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Get(s"/api/auctions/$auctionId/lots/$lotId") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        val lotResponse = responseAs[JsObject].as[LotResponse]
        val user        = UserSession.findUser(validUserToken.token)
        lotResponse shouldEqual LotResponse(
          UUID.fromString(auctionId),
          UUID.fromString(lotId),
          1,
          Some(Bid(user.get.id, bidRequest.bid, bidRequest.maxBid))
        )
      }
    }

    "handle failure of getting lot by lotId request on closed state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Get(s"/api/auctions/$auctionId/lots/$lotId") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle failure of getting lot by lotId request on finished state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Get(s"/api/auctions/$auctionId/lots/$lotId") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle get lots by auction request on inProgress state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val bidRequest           = BidRequest(100, None)
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""
      var lotId2               = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId2 = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/lots/$lotId", bidRequest) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Get(s"/api/auctions/$auctionId/lots") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        val lotResponse = responseAs[JsArray].as[List[LotResponse]]
        val user        = UserSession.findUser(validUserToken.token)

        lotResponse should have size 2
        lotResponse should contain(
          LotResponse(
            UUID.fromString(auctionId),
            UUID.fromString(lotId),
            1,
            Some(Bid(user.get.id, bidRequest.bid, bidRequest.maxBid))
          )
        )
        lotResponse should contain(LotResponse(UUID.fromString(auctionId), UUID.fromString(lotId2), 0, None))
      }
    }

    "handle failure of getting lots by auction request on closed state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Get(s"/api/auctions/$auctionId/lots") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "handle failure of getting lots by auction request on finished state" in {
      val auctionManager       = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val auctionRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route     = new LotServiceImpl(auctionManager).route
      val lotDetails           = LotDetails(0, None)
      var auctionId            = ""
      var lotId                = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        lotId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> auctionRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Get(s"/api/auctions/$auctionId/lots") ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }
  }
}
