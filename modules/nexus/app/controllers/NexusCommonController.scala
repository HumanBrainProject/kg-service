
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

package nexus.controllers

import common.helpers.ResponseHelper._
import javax.inject.{Inject, Singleton}
import nexus.helpers.{NexusHelper, NexusSpaceHandler}
import play.api.{Configuration, Logger}
import play.api.http.HttpEntity
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NexusCommonController @Inject()(cc: ControllerComponents, config:Configuration)(implicit ec: ExecutionContext, ws: WSClient)
  extends AbstractController(cc) {
  val logger = Logger(this.getClass)
  val apiEndpoint = config.get[String]("idm.api")
  val nexusEndpoint = config.get[String]("nexus.endpoint")
  val iamEndpoint = config.get[String]("nexus.iam")
  val orgNamePattern = "[a-z0-9]{3,}"

  def createPrivateSpace(): Action[AnyContent] = Action.async { implicit request =>
    val tokenOpt = request.headers.toSimpleMap.get("Authorization")
    tokenOpt match {
      case Some(token) =>
        request.body.asJson.map { jsonBody =>
          val groupName: String = (jsonBody \ "name").as[String].toLowerCase
          val isValidOrgName: Boolean = groupName.matches(orgNamePattern)
          if (isValidOrgName) {
            val description: String = (jsonBody \ "description").as[String]
            val nexusGroupName = s"nexus-$groupName"
            val adminGroupName = nexusGroupName + "-admin"
            for {
              nexusGroups <- NexusSpaceHandler.createGroups(nexusGroupName, adminGroupName, description, token, apiEndpoint)
              nexusOrg <- NexusSpaceHandler.createNexusOrg(groupName, token, nexusEndpoint)
              iamRights <- NexusSpaceHandler.grantIAMrights(groupName, token, iamEndpoint)
            } yield {
              val res = List(s"OIDC group creation result: ${nexusGroups.statusText}\t content: ${nexusGroups.body}",
                s"Nexus organization creation result: ${nexusOrg.statusText}\t content: ${nexusOrg.body}",
                s"ACLs creation result: ${iamRights.statusText}\t content: ${iamRights.body}"
              )
              Ok(s"${res.mkString("\n")}")
            }
          } else {
            Future.successful(BadRequest("Invalid group name for nexus organization"))
          }
        }.getOrElse(Future.successful(BadRequest("Empty body")))
      case _ => Future.successful(Unauthorized)
    }

  }


  def createSchema(organization: String, domain: String, entityType: String, version: String): Action[AnyContent] = Action.async { implicit request =>
    val tokenOpt = request.headers.toSimpleMap.get("Authorization")
    tokenOpt match {
      case Some(token) =>
        NexusHelper.createSchema(nexusEndpoint, organization, entityType, s"$organization/$domain", version, token).map {
            response =>
              response.status match {
                case 200 =>
                  Ok(response.body)
                case _ =>
                  Result(ResponseHeader(response.status, flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](response.headers))),
                    HttpEntity.Strict(response.bodyAsBytes, getContentType(response.headers)))
              }
          }
      case None =>
        Future.successful(Unauthorized)
    }
  }
}




