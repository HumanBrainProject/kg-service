/*
 * Copyright 2018 - 2021 Swiss Federal Institute of Technology Lausanne (EPFL)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This open source software code was developed in part or in whole in the
 * Human Brain Project, funded from the European Union's Horizon 2020
 * Framework Programme for Research and Innovation under
 * Specific Grant Agreements No. 720270, No. 785907, and No. 945539
 * (Human Brain Project SGA1, SGA2 and SGA3).
 *
 */
package controllers

import akka.util.ByteString
import com.google.inject.Inject
import play.api.http.HttpEntity
import play.api.mvc._
import services.GithubHookService

import scala.concurrent.{ExecutionContext, Future}

class WebhookController @Inject()(
  cc: ControllerComponents,
  githubHookService: GithubHookService
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  def github(gitlabProjectId: String, token: String): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson match {
      case Some(json) =>
        githubHookService
          .postToGitlab(json, gitlabProjectId, token)
          .map { res =>
            Result(
              header = ResponseHeader(
                res.status,
                res.headers.map(s => s._1 -> s._2.mkString(" "))
              ),
              body = HttpEntity.Strict(ByteString(res.body), None)
            )
          }
      case None =>
        Future {
          BadRequest("Missing payload")
        }
    }

  }
}
