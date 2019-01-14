/*
 *   Copyright (c) 2018, EPFL/Human Brain Project PCO
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package services

import java.util.concurrent.TimeUnit

import com.google.inject.Inject
import helpers.ESHelper
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.{Configuration, Logger}
import play.api.http.Status._
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient
import play.api.mvc.Headers
import models.user.{NexusUser, OIDCUser}
import play.filters.csrf.CSRF.Token

import scala.concurrent.duration.FiniteDuration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class OIDCAuthService @Inject()(
  config: ConfigurationService,
  eSService: ESService,
  credentialsService: CredentialsService,
  @NamedCache("userinfo-cache") cache: AsyncCacheApi,
  nexusService: NexusService,
  ws: WSClient
)(implicit ec: ExecutionContext)
    extends AuthService {

  private val techAccessToken = "techAccessToken"
  val logger = Logger(this.getClass)

  object cacheService extends CacheService

  override type U = Option[NexusUser]

  /**
    * Fetch user info from cache or OIDC API
    * @param headers the header containing the user's token
    * @return An option with the UserInfo object
    */
  override def getUserInfo(headers: Headers): Future[Option[NexusUser]] = {
    val token = headers.get("Authorization").getOrElse("")
    getUserInfoWithCache(token)
  }

  /**
    * Query OIDC for user info
    * @param token The user's token
    * @return An option with the UserInfo object
    */
  def getUserInfoFromToken(token: String): Future[Option[NexusUser]] = {
    ws.url(config.oidcUserInfoEndpoint).addHttpHeaders("Authorization" -> token).get().flatMap { res =>
      res.status match {
        case OK =>
          nexusService.listAllNexusResult(s"${config.nexusEndpoint}/v0/organizations?fields=all", token).map { re =>
            val orgs = re.map { js =>
              val s = (js \ "source" \ "@id").as[String]
              s.splitAt(s.lastIndexOf("/"))._2.substring(1)
            }
            val oIDCUser = res.json.as[OIDCUser]
            Some(new NexusUser(oIDCUser.id, oIDCUser.name, oIDCUser.email, oIDCUser.groups, orgs))
          }
        case _ => Future.successful(None)
      }
    }
  }

  /**
    * From a UserInfo object returns the index accessible in ES
    * @param userInfo The user info
    * @return A list of accessible index in ES
    */
  def groups(userInfo: Option[NexusUser]): Future[List[String]] = {
    userInfo match {
      case Some(info) =>
        for {
          esIndices <- eSService.getEsIndices()
        } yield {
          val kgIndices = esIndices.filter(_.startsWith("kg_")).map(_.substring(3))
          val nexusGroups = info.groups
          val resultingGroups = ESHelper.filterNexusGroups(nexusGroups).filter(group => kgIndices.contains(group))
          logger.debug(esIndices + "\n" + kgIndices + "\n " + nexusGroups)
          resultingGroups.toList
        }
      case _ => Future.successful(List())
    }
  }

  /**
    * Fetch a UserInfo object from the cache or through the OIDC API
    * @param token The token from the user
    * @return An option with the UserInfo object
    */
  def getUserInfoWithCache(token: String): Future[Option[NexusUser]] = {
    cacheService.getOrElse[NexusUser](cache, token) {
      getUserInfoFromToken(token).map {
        case Some(userInfo) =>
          cache.set(token, userInfo, config.cacheExpiration)
          Some(userInfo)
        case _ =>
          None
      }
    }
  }

  def getTechAccessToken(): Future[String] = {
    cacheService.get[String](cache, techAccessToken).flatMap {
      case Some(token) => Future.successful(token)
      case _ =>
        val clientCred = credentialsService.getClientCredentials()
        refreshAccessToken(clientCred)
    }
  }

  def refreshAccessToken(clientCredentials: ClientCredentials): Future[String] = {
    ws.url(config.oidcTokenEndpoint)
      .withQueryStringParameters(
        "client_id"     -> clientCredentials.clientId,
        "client_secret" -> clientCredentials.clientSecret,
        "refresh_token" -> clientCredentials.refreshToken,
        "grant_type"    -> "refresh_token"
      )
      .get()
      .map { result =>
        result.status match {
          case OK =>
            val token = s"Bearer ${(result.json \ "access_token").as[String]}"
            cache.set(techAccessToken, token, FiniteDuration(20, TimeUnit.MINUTES))
            token
          case _ =>
            logger.error(s"Error: while fetching tech account access token $result")
            throw new Exception("Could not fetch access token for tech account")
        }
      }
  }
}
