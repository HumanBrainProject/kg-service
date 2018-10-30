
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

import play.api.libs.json.{JsObject, Json}

case class FormRegistry(registry: JsObject)

trait FormRegistryService {

  def filterOrgs(formRegistry: FormRegistry, orgs: Seq[String]): FormRegistry = {
    formRegistry.copy(
        registry = Json.toJson(formRegistry.registry.value.filter{
        entity => orgs.contains(entity._1)
      }).asOpt[JsObject].getOrElse(Json.obj())
    )
  }
}