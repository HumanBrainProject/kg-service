
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

package nexus.helpers

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.libs.ws.{EmptyBody, WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}

object NexusSpaceHandler {
  val logger = Logger(this.getClass)

  def createGroups(nexusGroupName: String, adminGroupName: String, groupDescription: String, token: String, endpoint: String)
                  (implicit ec: ExecutionContext, wSClient: WSClient): Future[WSResponse] = {
    val payload = createGroupPayload(nexusGroupName, groupDescription)
    val adminPayload = createGroupPayload(adminGroupName, groupDescription)
    val response = for {
      readGroup <- wSClient.url(endpoint + s"/group").addHttpHeaders("Authorization" -> token).post(payload)
      adminGroup <- wSClient.url(endpoint + s"/group").addHttpHeaders("Authorization" -> token).post(adminPayload)
    } yield (readGroup, adminGroup)

    response.flatMap { case (readGroup, adminGroup) =>
      (readGroup.status, adminGroup.status) match {
        case (201, 201) =>
          logger.debug(s"Groups created: $nexusGroupName, $adminGroupName")
          grantAdminToGroup(nexusGroupName, adminGroupName, token, endpoint)
        case (201, _) => Future.successful(adminGroup)
        case (_, 201) => Future.successful(readGroup)
        case (_, _) =>
          logger.error(s"Could not create group: \n group response :${readGroup.statusText} ${readGroup.body} \n admin group response : ${adminGroup.statusText}${adminGroup.body}")
          Future.successful(readGroup)
      }
    }
  }

  private def grantAdminToGroup(nexusGroupName: String, adminGroupName: String, token: String, endpoint: String)
                               (implicit ec: ExecutionContext, wSClient: WSClient): Future[WSResponse] = {
    val adminEnpoint = endpoint + s"/group/$adminGroupName/admin-groups/${IDMHelper.nexusAdmin}"
    val groupEnpoint = endpoint + s"/group/$nexusGroupName/admin-groups/$adminGroupName"

    val response = for {
      readGroup <- wSClient.url(groupEnpoint).addHttpHeaders("Authorization" -> token, "Content-length" -> "0").post(EmptyBody)
      adminGroup <- wSClient.url(adminEnpoint).addHttpHeaders("Authorization" -> token, "Content-length" -> "0").post(EmptyBody)
    } yield (readGroup, adminGroup)

    response.map { case (readGroup, adminGroup) =>
      (readGroup.status, adminGroup.status) match {
        case (201, 201) => readGroup
        case (201, _) => adminGroup
        case (_, 201) => readGroup
        case (_, _) =>
          logger.error(s"Could not assign group as admin: \n group response :${readGroup.body} \n admin group response : ${adminGroup.body}")
          readGroup

      }
    }
  }

  private def createGroupPayload(nexusGroupName: String, groupDescription: String): JsObject = {
    Json.obj(
      "description" -> groupDescription,
      "name" -> nexusGroupName
    )
  }

  def createNexusOrg(groupName: String, token: String, nexusEndpoint: String)
                    (implicit ec: ExecutionContext, wSClient: WSClient): Future[WSResponse] = {
    val payload = Json.obj(
      "@context" -> Json.obj(
        "schema" -> "http://schema.org"
      ),
      "schema:name" -> groupName
    )
    wSClient.url(nexusEndpoint + s"/v0/organizations/$groupName")
      .addHttpHeaders("Authorization" -> token)
      .put(payload)
  }

  def grantIAMrights(groupName: String, token: String, iamEndpoint: String)(implicit wSClient: WSClient): Future[WSResponse] = {
    val endpoint = iamEndpoint + s"/v0/acls/kg/$groupName"
    val payload = aclPayload( Seq(groupName, s"${groupName}-admin"), Seq(Seq("read"), Seq("read", "write", "own", "publish")) )
    wSClient.url(endpoint).addHttpHeaders("Authorization" -> token).put(payload)
  }

  private def aclPayload(groupsName: Seq[String], groupsGrants: Seq[Seq[String]]): JsObject = {
    val aclsContent = JsArray(
      groupsName.zip(groupsGrants).map {
        case (groupName, groupGrants) =>
          Json.obj(
            "identity" -> Json.obj(
              "realm" -> "HBP",
              "group" -> s"nexus-$groupName",
              "@type" -> "GroupRef"
            ),
            "permissions" -> JsArray(
              groupGrants.map(JsString(_))
            )
          )
      }
    )
    Json.obj(
      "acl" -> aclsContent
    )
  }

  def deleteOrg(groupName: String, token: String, nexusEndpoint: String)(implicit wSClient: WSClient): Future[WSResponse] = {
    wSClient
      .url(nexusEndpoint + s"/v0/organizations/$groupName?rev=1")
      .addHttpHeaders("Authorization" -> token)
      .delete()
  }

  def deleteGroup(groupName: String, token: String, apiEndpoint: String)(implicit wSClient: WSClient): Future[WSResponse] = {
    wSClient.url(apiEndpoint + s"/group/$groupName").addHttpHeaders("Authorization" -> token).delete()
  }

  def removeACLS(groupName: String, token: String, iamEndpoint: String)(implicit wSClient: WSClient): Future[WSResponse] = {
    wSClient.url(iamEndpoint + s"/v0/acls/kg/$groupName").addHttpHeaders("Authorization" -> token).delete()
  }
}