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

package services

import helpers.ESHelper
import javax.inject.Inject
import monix.eval.Task
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext

class ESService @Inject()(wSClient: WSClient, configuration: ConfigurationService)(implicit ec: ExecutionContext) {

  def getEsIndices(): Task[List[String]] = {
    Task.deferFuture(wSClient.url(s"${configuration.esHost}/${ESHelper.indicesPath}?format=json").get()).map { res =>
      val j = res.json
      j.as[List[JsValue]].map(json => (json \ "index").as[String])
    }
  }

  /**
    * Fetch instance from es by id from the public index
    * @param dataType The type of the instance
    * @param id The id of the instance
    * @return a json representation of the instance
    */
  def getDataById(dataType: String, id: String): Task[Either[WSResponse, JsValue]] = {
    Task.deferFuture(wSClient.url(s"${configuration.esHost}/kg_public/$dataType/$id?format=json").get()).map { res =>
      res.status match {
        case OK => Right(res.json)
        case _  => Left(res)
      }
    }
  }

}
