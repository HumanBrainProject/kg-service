package helpers

import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

object ESHelper {

  val publicIndex: String = "kg_public"
  val indicesPath : String = "_cat/indices"

  def filterNexusGroups(groups: List[String], formatName: String => String = ESHelper.formatGroupName) = {
    groups.filter(s => s.startsWith("nexus-") && !s.endsWith("-admin")).map(formatName)
  }

  def formatGroupName(name: String):String = {
    name.replace("nexus-", "")
  }

  def transformToIndex(s: String): String = {
    s"kg_$s"
  }

  def getEsIndices(esEndpoint:String)(implicit wSClient: WSClient ,executionContext: ExecutionContext) : Future[List[String]] = {
    wSClient.url(esEndpoint + s"/${ESHelper.indicesPath}?format=json").get().map{ res =>
      val j = res.json
      j.as[List[JsValue]].map( json =>
        (json \ "index").as[String]
      )
    }
  }


}
