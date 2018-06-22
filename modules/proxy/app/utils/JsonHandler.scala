package proxy.utils

import play.api.libs.json._

object JsonHandler {

  def findPathForValue(value: String, path: JsPath, root: JsValue): Seq[JsPath] = {
    var res = Seq.empty[JsPath]
    root match {
      case o:JsObject =>
        val it = o.fields.iterator
        while (it.hasNext) {
          val e = it.next()
          res = res ++ findPathForValue(value, path \ (e._1), e._2)
        }
      case JsString(x) if x==value =>
        res = res :+ path
      case JsArray(x) =>
        val it = x.zipWithIndex.iterator
        while (it.hasNext) {
          val e = it.next()
          res = res ++ findPathForValue(value, path(e._2), e._1)
        }
      case _ => None
    }
    res
  }

  def findPathForKey(key: String, path: JsPath, root: JsValue): Seq[JsPath] = {
    var res = Seq.empty[JsPath]
    root match {
      case o:JsObject =>
        val it = o.fields.iterator
        while (it.hasNext) {
          val e = it.next()
          if (e._1 == key) {
            res = res :+ path
            res = res ++ findPathForKey(key, path \ (e._1), e._2)
          } else {
            res = res ++ findPathForKey(key, path \ (e._1), e._2)
          }
        }
      case JsArray(x) =>
        val it = x.zipWithIndex.iterator
        while (it.hasNext) {
          val e = it.next()
          res = res ++ findPathForKey(key, path(e._2), e._1)
        }
      case _ => None
    }
    res
  }

}
