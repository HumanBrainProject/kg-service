package helpers

import controllers.InstanceController
import play.api.libs.json._


/*
    Build a flatten view of a JsObject by setting path info into key
 */
object JsFlattener {

  def apply(js: JsValue): Seq[(JsPath, JsValue)] = {
    buildFlatObject(js).fields.map {
      case (pathString, value) => (buildJsPathFromString(pathString), value)
    }
  }

  def buildFlatObject(js: JsValue): JsObject = flatten(js)

  def concat(oldKey: String, newKey: String): String = {
    if (oldKey.nonEmpty) s"$oldKey%%%$newKey" else newKey
  }

  def flatten(js: JsValue, prefix: String = ""): JsObject = {
    if (!js.isInstanceOf[JsObject]) return Json.obj(prefix -> js)
    js.as[JsObject].fields.foldLeft(Json.obj()) {
      case (o, (k, value)) => {
        o.deepMerge(value match {
          case jsArr: JsArray => jsArr.as[Seq[JsValue]].zipWithIndex.foldLeft(o) {
            case (o, (n, i: Int)) => o.deepMerge(
              flatten(n.as[JsValue], s"${concat(prefix, k)}[$i]")
            )
          }
          case jsObj: JsObject => flatten(jsObj, concat(prefix, k))
          case other => Json.obj(concat(prefix, k) -> other.as[JsValue])
        })
      }
    }
  }

  // build a JsPath from a string path of form xx/yy/zz
  def buildJsPathFromString(stringPath: String): JsPath = {
    val path = stringPath.split("%%%").foldLeft(JsPath()) {
      case (jsPath, subPathString) =>
        // detect array idx
        val extractIdx = ".*\\[(\\d+)\\]$".r
        subPathString match {
          case extractIdx(idx) =>
            jsPath ++ (__ \ subPathString \ idx.toInt)
          case _ =>
            jsPath ++ (__ \ subPathString)
        }
    }
    path
  }

}