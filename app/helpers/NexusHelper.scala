package helpers

import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

object NexusHelper {

  val schemaDefinition = """
    {
      "@type": "owl:Ontology",
      "@context": {
          "datatype": {
              "@id": "sh:datatype",
              "@type": "@id"
          },
          "name": "sh:name",
          "path": {
              "@id": "sh:path",
              "@type": "@id"
          },
          "property": {
              "@id": "sh:property",
              "@type": "@id"
          },
          "targetClass": {
              "@id": "sh:targetClass",
              "@type": "@id"
          },
          "${org}": "http://hbp.eu/${org}#",
          "schema": "http://schema.org/",
          "sh": "http://www.w3.org/ns/shacl#",
          "owl": "http://www.w3.org/2002/07/owl#",
          "xsd": "http://www.w3.org/2001/XMLSchema#",
          "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          "shapes": {
              "@reverse": "rdfs:isDefinedBy",
              "@type": "@id"
          }
      },
      "shapes": [
        {
          "@id": "${org}:${entityType}Shape",
          "@type": "sh:NodeShape",
          "property": [
            {
              "datatype": "xsd:string",
              "path": "${org}:origin"
            }
          ],
          "targetClass": "${org}:${entityType}"
        }
      ]
    }
    """

  def createSchema(org: String, entityType: String, space: String, version: String, token: String)(implicit config: Configuration, ws: WSClient, ec: ExecutionContext): Future[WSResponse] = {

    val nexusUrl = config.get[String](s"nexus.endpoint")
    val schemaUrl = s"${nexusUrl}/v0/schemas/${space}/${entityType.toLowerCase}/${version}"
    ws.url(schemaUrl).addHttpHeaders("Authorization" -> token).get().flatMap{
      response => response.status match {
        case 200 => // schema exists already
          Future.successful(response)
        case 404 => // schema not found, create it
          val schemaContent = Json.parse(schemaDefinition.replace("${entityType}", entityType).replace("${org}", org))
          ws.url(schemaUrl).addHttpHeaders("Authorization" -> token).put(schemaContent).flatMap{
            schemaCreationResponse => schemaCreationResponse.status match {
              case 201 => // schema created, publish it
                ws.url(s"$schemaUrl/config?rev=1").addHttpHeaders("Authorization" -> token).patch(
                  Json.obj("published" -> JsBoolean(true))
                )
              case _ =>
                Future.successful(response)
            }
          }
        case _ =>
          Future.successful(response)
      }
    }


  }

  def listAllNexusResult(url: String, token: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Seq[JsValue]] = {
    val sizeLimit = 5
    val initialUrl = (url.contains("?size="), url.contains("&size=")) match {
      case (true, _) => url
      case (_ , true) => url
      case (false, false) => if(url.contains("?")) s"$url&size=$sizeLimit" else s"$url?size=$sizeLimit"
    }

    ws.url(initialUrl).addHttpHeaders("Authorization" -> token).get().flatMap {
      response => response.status match {
        case 200 =>
          val firstResults = (response.json \ "results").as[JsArray].value
          (response.json \ "links" \ "next").asOpt[String] match {
            case Some(nextLink) =>
              // compute how many additional call will be needed
              val nbCalls = ((response.json \ "total").as[Int] / (sizeLimit.toDouble)).ceil.toInt
              Range(1, nbCalls).foldLeft(Future.successful((nextLink, firstResults))) {
                case (previousCallState, callIdx) =>
                  previousCallState.flatMap {
                    case (nextUrl, previousResult) =>
                      if (nextUrl.nonEmpty) {
                        ws.url(nextUrl).addHttpHeaders("Authorization" -> token).get().map { response =>
                          response.status match {
                            case 200 =>
                              val newUrl = (response.json \ "links" \ "next").asOpt[String].getOrElse("")
                              val newResults = previousResult ++ (response.json \ "results").as[JsArray].value
                              (newUrl, newResults)
                            case _ =>
                              ("", previousResult)
                          }
                        }
                      } else {
                        Future.successful(("", previousResult))
                      }
                  }
              }.map(_._2)
            case _ =>
              Future.successful(firstResults)
          }
        case _ =>
          Future.successful(Seq.empty[JsValue])
      }
    }
  }



}
