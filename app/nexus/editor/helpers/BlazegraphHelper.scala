package nexus.editor.helpers

import play.api.libs.json.{JsArray, JsValue, Json}

object BlazegraphHelper {
  def extractResult(json:JsValue): JsArray = {
    (json \ "results" \ "bindings").as[JsArray]
  }
}
