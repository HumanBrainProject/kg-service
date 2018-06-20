package services.Authentication

import com.google.inject.Inject
import helpers.ESHelper
import models.UserInfo
import play.api.{Configuration, Logger}
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Headers

import scala.concurrent.{ExecutionContext, Future}

class OIDCAuthService @Inject()(config: Configuration)(implicit ec: ExecutionContext,  ws: WSClient) extends AuthService {
  val oidcUserInfoEndpoint = config.get[String]("auth.userinfo")
  val esHost: String = config.get[String]("es.host")
  val logger = Logger(this.getClass)

  override type U = Option[UserInfo]

  override def getUserInfo(headers: Headers): Future[Option[UserInfo]] = {
    val token = headers.get("Authorization").getOrElse("")
    ws.url(oidcUserInfoEndpoint).addHttpHeaders("Authorization" -> token).get().map {
      res =>
        res.status match {
          case OK =>
            Some(UserInfo(res.json.as[JsObject]))
          case _ => None
        }
    }
  }

  def groups(userInfo: Option[UserInfo]): Future[List[String]] = {
    userInfo match {
      case Some(info) =>
        for {
          esIndices <- ESHelper.getEsIndices(esHost)
        } yield {
          val kgIndices = esIndices.filter(_.startsWith("kg_")).map(_.substring(3))
          val nexusGroups =  info.groups
          val resultingGroups = OIDCAuthService.extractNexusGroup(nexusGroups).filter(group => kgIndices.contains(group))
          logger.debug(esIndices + "\n" + kgIndices + "\n " + nexusGroups)
          resultingGroups.toList
        }
      case _ => Future.successful(List())
    }
  }
}

object OIDCAuthService {

  def extractNexusGroup(groups: Seq[String]): Seq[String] = {
      ESHelper.filterNexusGroups(groups)
  }
}