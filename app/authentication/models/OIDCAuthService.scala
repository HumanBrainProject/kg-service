package authentication.models

import com.google.inject.Inject
import core.ConfigurationHandler
import nexus.editor.helpers.ESHelper
import play.api.http.Status._
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient
import play.api.mvc.Headers
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

class OIDCAuthService (implicit ec: ExecutionContext,  ws: WSClient) extends AuthService {
  val oidcUserInfoEndpoint = ConfigurationHandler.getString("auth.userinfo")
  val esHost: String = ConfigurationHandler.getString("es.host")
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