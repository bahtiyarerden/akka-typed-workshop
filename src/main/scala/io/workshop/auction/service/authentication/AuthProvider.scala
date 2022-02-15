package io.workshop.auction.service.authentication

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.AuthenticationFailedRejection.{ Cause, CredentialsMissing, CredentialsRejected }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, Directive1 }
import akka.util.Timeout

import scala.concurrent.duration._

object AuthProvider {
  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  def authenticated: Directive1[User] =
    for {
      credentials <- extractCredentials
      result <- {
        credentials match {
          case Some(c) if c.scheme.equalsIgnoreCase("Bearer") => authenticate(c.token)
          case _                                              => rejectUnauthenticated(CredentialsMissing)
        }
      }
    } yield result

  private def authenticate(token: String): Directive1[User] = {
    val result = UserSession.findUser(token)
    result match {
      case Some(user) => provide(user)
      case None       => rejectUnauthenticated(CredentialsRejected)
    }
  }

  private def rejectUnauthenticated(cause: Cause): Directive1[User] =
    reject(AuthenticationFailedRejection(cause, HttpChallenge("Bearer", "auction-workshop")))

}
