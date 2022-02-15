package io.workshop.auction.service.exception

import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler

sealed trait ServiceException

object ServiceException {
  def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case err: UnfitBidException       => complete(HttpResponse(StatusCodes.Forbidden, entity = err.getMessage))
      case err: EntityNotFoundException => complete(HttpResponse(StatusCodes.NotFound, entity = err.getMessage))
      case err: IllegalAuctionStateException =>
        complete(HttpResponse(StatusCodes.MethodNotAllowed, entity = err.getMessage))
      case err: LotsNotReceivedException =>
        complete(HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage))
      case err: Exception => complete(HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage))
    }
}

final case class IllegalAuctionStateException(
  private val message: String          = "",
  private val cause: Option[Throwable] = None
) extends RuntimeException(message)
    with ServiceException {
  cause.foreach(initCause)
}

final case class EntityNotFoundException(private val message: String = "", private val cause: Option[Throwable] = None)
    extends RuntimeException(message)
    with ServiceException {
  cause.foreach(initCause)
}

final case class LotNotFoundException(private val message: String = "", private val cause: Option[Throwable] = None)
    extends RuntimeException(message)
    with ServiceException {
  cause.foreach(initCause)
}

final case class UnfitBidException(private val message: String = "", private val cause: Option[Throwable] = None)
    extends RuntimeException(message)
    with ServiceException {
  cause.foreach(initCause)
}
