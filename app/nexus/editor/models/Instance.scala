package nexus.editor.models

import nexus.common.models.NexusPath
import play.api.libs.json.{JsObject, JsValue}

case class Instance(nexusUUID: String, nexusPath: NexusPath, content:JsObject){

  def id():String = {
    s"${this.nexusPath}/${this.nexusUUID}"
  }

  def extractUpdateInfo(): (String, Int, String) = {
    ((this.content \"@id").as[String],
      (this.content \ "nxv:rev").as[Int],
      (this.content \ "http://hbp.eu/manual#updater_id").asOpt[String].getOrElse("")
    )
  }
}

object Instance {
  def apply(jsValue: JsValue): Instance = {
    val (id, path) = extractIdAndPath(jsValue)
    new Instance(id, path , jsValue.as[JsObject])
  }

  def extractIdAndPath(jsValue: JsValue): (String, NexusPath) = {
    val nexusUrl = (jsValue \ "@id").as[String]
    val nexusId = getIdfromURL(nexusUrl)
    val datatype = nexusId.splitAt(nexusId.lastIndexOf("/"))
    val nexusType = NexusPath(datatype._1.split("/").toList)
    (datatype._2.substring(1) , nexusType)
  }
  def getIdfromURL(url: String): String = {
    assert(url contains "v0/data/")
    url.split("v0/data/").tail.head
  }

  // extract id, rev and userId of updator for this update

}


