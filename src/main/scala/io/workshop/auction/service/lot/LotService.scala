package io.workshop.auction.service.lot

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher, Route }
import akka.stream.scaladsl.Source
import akka.{ Done, NotUsed }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait LotService extends PlayJsonSupport {

  def createLot(auctionId: UUID, lot: CreateLotRequest): Future[Try[LotIdResponse]]

  def makeBid(auctionId: UUID, lotId: UUID, userId: UUID, bid: BidRequest): Future[Try[Done]]

  def getLotsByAuction(auctionId: UUID): Future[Try[Source[LotResponse, NotUsed]]]

  def getLotById(auctionId: UUID, lotId: UUID): Future[Try[LotResponse]]

  private val root: PathMatcher[Tuple1[UUID]] = "api" / "auctions" / JavaUUID
  val route: Route = pathPrefix(root) { auctionId =>
    authenticated { user =>
      handleExceptions(ServiceException.exceptionHandler) {
        concat(
          path("lots") {
            concat(
              post {
                entity(as[CreateLotRequest]) { lot =>
                  val resp = createLot(auctionId, lot)
                  complete(resp)
                }
              },
              get {
                val resp = getLotsByAuction(auctionId)
                complete(resp)
              }
            )
          },
          path("lots" / JavaUUID) { lotId =>
            concat(
              put {
                entity(as[BidRequest]) { bid =>
                  val resp = makeBid(auctionId, lotId, user.id, bid)
                  complete(resp)
                }
              },
              get {
                val resp = getLotById(auctionId, lotId)
                complete(resp)
              }
            )
          }
        )
      }
    }
  }
}
