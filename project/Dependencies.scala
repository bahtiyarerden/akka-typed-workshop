import sbt._

object Dependencies {

  object Versions {
    val akka         = "2.6.15"
    val akkaHttp     = "10.2.5"
    val akkaHttpJson = "1.36.0"
    val logback      = "1.2.3"
    val scalaTest    = "3.1.4"
    val levelDb      = "1.8"
  }

  lazy val levelDb = "org.fusesource.leveldbjni" % "leveldbjni-all"  % Versions.levelDb
  lazy val logback = "ch.qos.logback"            % "logback-classic" % Versions.logback

  object akka {
    lazy val actor            = "com.typesafe.akka" %% "akka-actor"             % Versions.akka
    lazy val typedActor       = "com.typesafe.akka" %% "akka-actor-typed"       % Versions.akka
    lazy val streamTyped      = "com.typesafe.akka" %% "akka-stream-typed"      % Versions.akka
    lazy val stream           = "com.typesafe.akka" %% "akka-stream"            % Versions.akka
    lazy val http             = "com.typesafe.akka" %% "akka-http"              % Versions.akkaHttp
    lazy val persistenceTyped = "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akka
    lazy val all              = Seq(actor, typedActor, stream, streamTyped, http, persistenceTyped)
  }

  object serialization {
    lazy val akkaJackson  = "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akka
    lazy val httpPlayJson = "de.heikoseeberger" %% "akka-http-play-json"        % Versions.akkaHttpJson
    lazy val all          = Seq(akkaJackson, httpPlayJson)
  }

  object test {
    lazy val akkaTestkit            = "com.typesafe.akka" %% "akka-testkit"             % Versions.akka
    lazy val akkaActorTestkitTyped  = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka
    lazy val akkaStreamTestkit      = "com.typesafe.akka" %% "akka-stream-testkit"      % Versions.akka
    lazy val akkaHttpTestkit        = "com.typesafe.akka" %% "akka-http-testkit"        % Versions.akkaHttp
    lazy val akkaPersistenceTestkit = "com.typesafe.akka" %% "akka-persistence-testkit" % Versions.akka
    lazy val scalaTest              = "org.scalatest"     %% "scalatest"                % Versions.scalaTest
    lazy val all =
      Seq(akkaTestkit, akkaActorTestkitTyped, akkaStreamTestkit, akkaHttpTestkit, akkaPersistenceTestkit, scalaTest)
        .map(_ % Test)
  }
}
