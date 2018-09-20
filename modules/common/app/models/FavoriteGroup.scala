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
package common.models

import play.api.libs.json._

case class FavoriteGroup(nexusId: String, name: String, favorites: Seq[Favorite])
object FavoriteGroup {


  import play.api.libs.functional.syntax._


  implicit val favGroupWrites = new Writes[FavoriteGroup] {
    def writes(favGroup: FavoriteGroup) = Json.obj(
      "nexusId" -> favGroup.nexusId,
      "name" -> favGroup.name,
      "favorites" -> Json.toJson(favGroup.favorites)
    )
  }

  implicit val favGroupReads: Reads[FavoriteGroup] = (
    (JsPath \ "nexusId").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "favorites").read[Seq[JsObject]]
        .map(jss => jss.map{js =>
            val instanceId = (js \ "instance" \ "@id").as[String].split("v0/data/").last
            val nexusId = (js \ "nexusId").as[String]
            Favorite(nexusId, instanceId)
          })
        .orElse(Reads.pure(Seq[Favorite]()))
    ) (FavoriteGroup.apply _)

  val valuesTransformer = __.read[JsArray].map[JsArray] {
    case JsArray(values) =>
      JsArray(values.map { e => Json.obj("instance" -> (e \ "@id").as[JsString], "nexusId" -> (e \ "nexusId").as[JsString])})
  }
}