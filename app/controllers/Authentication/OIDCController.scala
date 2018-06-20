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
package controllers.Authentication

import com.google.inject.Inject
import helpers.{AuthenticatedUserAction, ESHelper}
import javax.inject.Singleton
import models.UserRequest
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.Authentication.OIDCAuthService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OIDCController @Inject()(cc: ControllerComponents,
                               authService: OIDCAuthService,
                               authenticatedUserAction: AuthenticatedUserAction)
                              (implicit ec: ExecutionContext, ws: WSClient, config: Configuration)
  extends AbstractController(cc) {
  val esHost: String = config.get[String]("es.host")
  val logger = Logger(this.getClass)

  def groups(): Action[AnyContent] = authenticatedUserAction.async { implicit request: UserRequest[AnyContent] =>
    authService.groups(request.user).map( l => Ok(Json.toJson(l)))
  }

  def groupsOptions(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok("").withHeaders("Allow" -> "GET, OPTIONS")
  }
}
