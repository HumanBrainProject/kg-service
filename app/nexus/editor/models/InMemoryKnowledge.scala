package nexus.editor.models

import core.ConfigurationHandler
import nexus.common.helpers.NexusHelper
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient

import concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.collection.immutable.HashSet

class InMemoryKnowledge(space: String) {

  // stores known schemas available in manual space
  var manualSchema = HashSet.empty[String]


  // This must be called by controller whenever it's needed to refresh known schema list
  def loadManualSchemaList(token: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    val schemaUrl = s"${ConfigurationHandler.getString("nexus.endpoint")}/v0/schemas/$space"
    // wait for schemas call to finish before going on
    val currentSchemas =Await.result(
      NexusHelper.listAllNexusResult(schemaUrl, token),
      1.seconds)

    if (currentSchemas.nonEmpty){
      val schemasIds = currentSchemas.map(js => (js.as[JsObject] \ "resultId").as[String])
      manualSchema = seqToHashSet[String](schemasIds)
    }
  }

  def seqToHashSet[T](seq: Seq[T]): HashSet[T] = {
    seq.toSet.foldLeft(HashSet.empty[T]){
      case (res, schema) => res + schema
    }
  }
}
