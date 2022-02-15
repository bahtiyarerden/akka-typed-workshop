package io.workshop.auction.service.authentication

import java.util.UUID

object UserSession {
  private val users: Map[String, User] = Map(
    "superSecretToken.0" -> User(UUID.randomUUID()),
    "superSecretToken.1" -> User(UUID.randomUUID()),
    "superSecretToken.2" -> User(UUID.randomUUID()),
    "superSecretToken.3" -> User(UUID.randomUUID()),
    "superSecretToken.4" -> User(UUID.randomUUID()),
    "superSecretToken.5" -> User(UUID.randomUUID()),
    "superSecretToken.6" -> User(UUID.randomUUID()),
    "superSecretToken.7" -> User(UUID.randomUUID()),
    "superSecretToken.8" -> User(UUID.randomUUID()),
    "superSecretToken.9" -> User(UUID.randomUUID())
  )

  def findUser(token: String): Option[User] =
    users.get(token)

}
