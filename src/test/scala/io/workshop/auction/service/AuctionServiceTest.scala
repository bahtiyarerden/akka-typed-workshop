package io.workshop.auction.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{ HttpChallenge, OAuth2BearerToken }
import akka.http.scaladsl.server.AuthenticationFailedRejection.{ CredentialsMissing, CredentialsRejected }
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{ JsArray, JsObject, JsString, JsValue }

import java.util.UUID

class AuctionServiceTest
    extends AnyWordSpec
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with Matchers
    with PlayJsonSupport {

  import akka.actor.typed.scaladsl.adapter._

  private lazy val testKit: ActorTestKit           = ActorTestKit()
  private implicit val typedSystem: ActorSystem[_] = system.toTyped

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val httpChallenge       = HttpChallenge("Bearer", "auction-workshop")
  private val missingCredentials  = AuthenticationFailedRejection(CredentialsMissing, httpChallenge)
  private val rejectedCredentials = AuthenticationFailedRejection(CredentialsRejected, httpChallenge)
  private val validUserToken      = OAuth2BearerToken("superSecretToken.3")
  private val nonValidUserToken   = OAuth2BearerToken("notSuperSecretToken.3")
  private val auctionToCreate     = JsObject(Map("name" -> JsString("Van Gogh's The Starry Night")))

  "AuctionService" must {

    "handle non-valid user authentication token" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route

      Post("/api/auctions", auctionToCreate) ~> addCredentials(nonValidUserToken) ~> apiRoutes ~> check {
        rejection shouldBe rejectedCredentials
      }
    }

    "handle undefined authentication header" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val auctionToCreate  = JsObject(Map("name" -> JsString("Van Gogh's The Starry Night")))

      Post("/api/auctions", auctionToCreate) ~> apiRoutes ~> check {
        rejection shouldBe missingCredentials
      }
    }

    "allow to create auction" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        (responseAs[JsValue] \ "id").isDefined shouldBe true
      }
    }

    "allow to list auctions" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val auctionToCreate2 = JsObject(Map("name" -> JsString("Pablo Picasso's Guernica")))
      val auctionToCreate3 = JsObject(Map("name" -> JsString("Turner's The Slave Ship")))

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
      Post("/api/auctions", auctionToCreate2) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
      Post("/api/auctions", auctionToCreate3) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
      Get("/api/auctions") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        responseAs[JsArray].value.nonEmpty shouldBe true
        responseAs[JsArray].value.length should equal(3)
        (responseAs[JsArray].value.head \ "id").isDefined shouldBe true
        (responseAs[JsArray].value.head \ "name").isDefined shouldBe true
      }
    }

    "allow to start auction" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
    }

    "not allow to start auction without lot" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "allow to end auction" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }
    }

    "handle not found auction exception" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val fakeUUID         = UUID.randomUUID()

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$fakeUUID/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.NotFound
      }
    }

    "handle illegal state exception on closed state" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }

    }

    "handle illegal state exception on started state" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }

    "do nothing after auction finished" in {
      val auctionManager   = testKit.spawn(AuctionManagerEntity(UUID.randomUUID().toString))
      val apiRoutes: Route = new AuctionServiceImpl(auctionManager).route
      val lotRoutes: Route = new LotServiceImpl(auctionManager).route
      val lotDetails       = LotDetails(0, None)
      var auctionId        = ""

      Post("/api/auctions", auctionToCreate) ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
        auctionId = (responseAs[JsValue] \ "id").as[String]
      }

      Post(s"/api/auctions/$auctionId/lots", lotDetails) ~> addCredentials(validUserToken) ~> lotRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/end") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.OK
      }

      Put(s"/api/auctions/$auctionId/start") ~> addCredentials(validUserToken) ~> apiRoutes ~> check {
        response.status shouldEqual StatusCodes.MethodNotAllowed
      }
    }
  }
}
