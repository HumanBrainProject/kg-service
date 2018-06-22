import sbt._
import Keys._
import sbt._
import Keys._
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys.routesGenerator
import play.routes.compiler.InjectedRoutesGenerator


object Common {
  val settings: Seq[Setting[_]] = Seq(
    organization := "eu.humanbrainproject",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.12.3",
    libraryDependencies ++= baseDependencies
  )

  val baseDependencies = Seq(
    "de.leanovate.play-mockws" %% "play-mockws" % "2.6.2" % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
    "org.mockito" % "mockito-core" % "2.19.0" % Test,
    "com.github.stijndehaes" %% "play-prometheus-filters" % "0.3.2"
  )

  val playDependencies = Seq(
    "com.typesafe.play" %% "play-iteratees" % "2.6.1",
    guice,
    ws
  )

  val playSettings = settings ++ Seq(
    resolvers += "Typesafe Simple Repository" at "http://repo.typesafe.com/typesafe/simple/maven-releases/",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= playDependencies
  )
}