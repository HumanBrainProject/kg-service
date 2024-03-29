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

package models.instance

import constants.JsonLDConstants
import models.NexusPath
import play.api.libs.json._

final case class NexusInstance(nexusUUID: Option[String], nexusPath: NexusPath, content: JsObject) {

  def id(): Option[String] = {
    this.nexusUUID.map(s => s"${this.nexusPath}/${s}")
  }

  def getField(fieldName: String): Option[JsValue] = content.value.get(fieldName)

  def merge(instance: NexusInstance): NexusInstance = {
    val content = this.content.value ++ instance.content.value
    this.copy(content = Json.toJson(content).as[JsObject])
  }
}

object NexusInstance {

  object Fields {
    val nexusRev = "nxv:rev"
  }

  import play.api.libs.functional.syntax._

  implicit val editorUserReads: Reads[NexusInstance] = (
    (JsPath \ JsonLDConstants.ID).read[String].map(NexusInstanceReference.fromUrl(_).id).map(Some(_)) and
    (JsPath \ JsonLDConstants.ID).read[String].map(NexusInstanceReference.fromUrl(_).nexusPath) and
    JsPath.read[JsObject]
  )(NexusInstance.apply _)
}
