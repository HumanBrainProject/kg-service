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
package models

import play.api.libs.json.JsValue

case class NexusPath(org: String, domain:String, schema: String, version:String) {

  override def toString() = {
    Seq(org, domain, schema, version).mkString("/")
  }
}

object NexusPath {
  def apply(list: List[String]): NexusPath = NexusPath(list.head, list(1), list(2), list(3))
  def apply(path: String): NexusPath = NexusPath(path.split("/").toList)

  def getNexusPathFromJson(jsonElement: JsValue): NexusPath = {
    val links = (jsonElement \ "links").as[JsValue]
    val schema = (links \ "schema").as[String]
    NexusPath(schema.splitAt(schema.indexOf("schema/"))._2.split("/").toList)
  }
}
