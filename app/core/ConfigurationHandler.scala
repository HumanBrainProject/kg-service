package core

import com.typesafe.config._
import play.api.{ConfigLoader, Configuration, PlayConfig}
import play.api.ConfigLoader._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.collection.JavaConverters._


object ConfigurationHandler {

  //val confFile = Play.application().classloader.getResourceAsStream("application.conf")
  val config = ConfigFactory.load("application.conf")

  def getString(path: String): String = config.getString(path)
  def getInt(path: String): Int = config.getInt(path)

  def getOptionalString(path: String): Option[String] = {
    if (config.hasPath(path)) Some(config.getString(path)) else None
  }
  def getOptionalInt(path: String): Option[Int] = {
    if (config.hasPath(path)) Some(config.getInt(path)) else None
  }
  def hasPath(path: String): Boolean = config.hasPath(path)

}
