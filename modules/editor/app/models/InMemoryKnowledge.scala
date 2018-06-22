
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

package editor.models


import nexus.helpers.NexusHelper
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient

import concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.collection.immutable.HashSet

class InMemoryKnowledge(endpoint: String, space: String) {

  // stores known schemas available in manual space
  var manualSchema = HashSet.empty[String]
  val nexusEndpoint = endpoint


  // This must be called by controller whenever it's needed to refresh known schema list
  def loadManualSchemaList(token: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    val schemaUrl = s"$nexusEndpoint/v0/schemas/$space"
    // wait for schemas call to finish before going on
    val currentSchemas =Await.result(
      NexusHelper.listAllNexusResult(schemaUrl, token),
      1.seconds)

    if (currentSchemas.nonEmpty){
      val schemasIds = currentSchemas.map(js => (js.as[JsObject] \ "resultId").as[String])
      manualSchema = seqToHashSet[String](schemasIds)
    }
  }

  def seqToHashSet[T](seq: Seq[T]): HashSet[T] = {
    seq.toSet.foldLeft(HashSet.empty[T]){
      case (res, schema) => res + schema
    }
  }
}
