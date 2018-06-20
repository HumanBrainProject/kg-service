package nexus.editor.helpers

import nexus.common.models.NexusPath
import play.api.libs.json.{JsArray, Json}

object NodeTypeHelper {

  def formatNodetypeList(jsArray: JsArray): JsArray = {
  val r = jsArray.value.map { js =>
  val tempId = (js \ "schema" \ "value").as[String]
  val path: String = tempId.split("v0/schemas/").tail.head
  val label = NexusPath.apply(path.split("/").toList).schema
  Json.obj("label" -> label.capitalize, "path" -> path)
}.sortBy( js => (js \ "label").as[String])(Ordering.fromLessThan[String]( _ < _ ))
  Json.toJson(r).as[JsArray]
}
}
