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

package helpers

import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

object ESHelper {

  val publicIndex: String = "kg_public"
  val indicesPath : String = "_cat/indices"

  def filterNexusGroups(groups: Seq[String], formatName: String => String = ESHelper.formatGroupName): Seq[String] = {

    groups.filter(s => s.startsWith("nexus-") && !s.endsWith("-admin")).map(formatName)
  }

  def formatGroupName(name: String):String = {
    name.replace("nexus-", "")
  }

  def transformToIndex(s: String): String = {
    s"kg_$s"
  }

  def getEsIndices(esEndpoint:String)(implicit wSClient: WSClient ,executionContext: ExecutionContext) : Future[List[String]] = {
    wSClient.url(esEndpoint + s"/${ESHelper.indicesPath}?format=json").get().map{ res =>
      val j = res.json
      j.as[List[JsValue]].map( json =>
        (json \ "index").as[String]
      )
    }
  }


}
