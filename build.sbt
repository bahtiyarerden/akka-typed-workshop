name := "akka-workshop"

version := "1.0"

scalaVersion := "2.12.6"
Test / fork := true

libraryDependencies ++= Dependencies.akka.all ++ Dependencies.serialization.all ++ Seq(Dependencies.levelDb) ++ Seq(
  Dependencies.logback
) ++ Dependencies.test.all
