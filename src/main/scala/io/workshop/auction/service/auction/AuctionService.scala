package io.workshop.auction.service.auction

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher, Route }
import akka.stream.scaladsl.Source
import akka.{ Done, NotUsed }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

trait AuctionService extends PlayJsonSupport {
  def createAuction(auction: CreateAuctionRequest): Future[AuctionIdResponse]

  def getAuctions: Source[AuctionResponse, Future[NotUsed]]

  def startAuction(auctionId: UUID): Future[Try[Done]]

  def endAuction(auctionId: UUID): Future[Try[Done]]

  // other state transitions

  private val root: PathMatcher[Unit]                 = "api" / "auctions"
  private val startAuction: PathMatcher[Tuple1[UUID]] = JavaUUID / "start"
  private val endAuction: PathMatcher[Tuple1[UUID]]   = JavaUUID / "end"

  val route: Route =
    pathPrefix(root) {
      authenticated { _ =>
        handleExceptions(ServiceException.exceptionHandler) {
          concat(
            pathEnd {
              concat(
                post {
                  entity(as[CreateAuctionRequest]) { auction =>
                    val resp = createAuction(auction)
                    complete(resp)
                  }
                },
                get {
                  val resp = getAuctions
                  complete(resp)
                }
              )
            },
            path(startAuction) { auctionId =>
              put {
                val resp = startAuction(auctionId)
                complete(resp)
              }
            },
            path(endAuction) { auctionId =>
              put {
                val resp = endAuction(auctionId)
                complete(resp)
              }
            }
          )
        }
      }
    }
}
