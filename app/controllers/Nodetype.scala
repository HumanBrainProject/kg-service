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
package controllers

import helpers.{BlazegraphHelper, FormHelper}
import javax.inject.Inject
import play.api.{Application, Configuration}
import play.api.http.HttpEntity
import play.api.libs.json.{JsArray, JsPath, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._
import helpers.ResponseHelper._

import scala.concurrent.{ExecutionContext, Future}

class Nodetype @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext, ws: WSClient, config: Configuration)
  extends AbstractController(cc) {


  val nexusEndpoint = config.getOptional[String]("nexus.endpoint").getOrElse("https://nexus-dev.humanbrainproject.org")
  val sparqlEndpoint = config.getOptional[String]("blazegraph.endpoint").getOrElse("http://localhost:9999")
  val blazegraphNameSpace = config.getOptional[String]("blazegraph.namespace").getOrElse("kg")
  // TODO Check for authentication and groups as for now everybody could see the whole graph
  def listWithBlazeGraph(privateSpace: String): Action[AnyContent] = Action.async { implicit request =>
    val sparqlPayload =
      s"""
         |SELECT ?schema  WHERE {
         |  ?instance a <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/Instance> .
         |  ?instance <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/deprecated> false .
         |  ?instance <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/schema> ?schema .
         |  ?schema <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/organization> <https://nexus-dev.humanbrainproject.org/v0/organizations/$privateSpace> .
         |}GROUP BY ?schema
         |
      """.stripMargin
    val start = System.currentTimeMillis()
    val res = ws.url(s"$sparqlEndpoint/bigdata/namespace/$blazegraphNameSpace/sparql").withQueryStringParameters("query" -> sparqlPayload, "format" -> "json").get().map[Result] {
      res =>
        res.status match {
          case 200 =>
            val arr = BlazegraphHelper.extractResult(res.json)
            val duration = System.currentTimeMillis() - start
            println(s"sparql query: \n$sparqlPayload\n\nduration: ${duration}ms")
            Ok(Nodetype.formatNodetypeList(arr))
          case _ =>
            Result(
              ResponseHeader(res.status, flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](res.headers))),
              HttpEntity.Strict(res.bodyAsBytes, getContentType(res.headers))
            )
        }
    }
    res
  }

  def list(privateSpace: String): Action[AnyContent] = Action {
    // Editable instance types are the one for which form creation is known
    Ok(FormHelper.editableEntitiyTypes)
  }
}

object Nodetype {

  def formatNodetypeList(jsArray: JsArray): JsArray = {
    val r = jsArray.value.map { js =>
      val tempId = (js \ "schema" \ "value").as[String]
      val path: String = tempId.split("v0/schemas/").tail.head
      val label = models.NexusPath.apply(path.split("/").toList).schema
      Json.obj("label" -> label.capitalize, "path" -> path)
    }.sortBy( js => (js \ "label").as[String])(Ordering.fromLessThan[String]( _ < _ ))
    Json.toJson(r).as[JsArray]
  }
}
