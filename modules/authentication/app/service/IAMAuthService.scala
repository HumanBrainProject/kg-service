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
package authentication.service

import authentication.models.{IAMAcl, IAMPermission}
import com.google.inject.Inject
import common.services.ConfigurationService
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.http.Status.OK

import scala.concurrent.{ExecutionContext, Future}

class IAMAuthService @Inject()(wSClient: WSClient, config: ConfigurationService)(implicit ec: ExecutionContext) {

  def getAcls(path: String, parameters: Seq[(String, String)]): Future[Either[WSResponse, List[IAMAcl]]] = {
    wSClient.url(s"${config.iamEndpoint}/v0/acls/kg/${path}")
      .withQueryStringParameters(parameters: _*)
      .get()
      .map {
        res =>
          res.status match {
            case OK => Right((res.json \ "acl").as[List[IAMAcl]])
            case _ => Left(res)
          }
      }
  }
}

object IAMAuthService {
  def hasAccess(acls: List[IAMAcl], iAMPermission: IAMPermission): Boolean = {
    acls.flatMap(_.permissions).contains(iAMPermission)
  }
}
