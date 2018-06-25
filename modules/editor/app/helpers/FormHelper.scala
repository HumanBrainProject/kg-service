
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

package editor.helpers

import common.models.NexusPath
import editor.models.Instance
import play.Play
import play.api.libs.json._


object FormHelper {

  val slashEscaper = "%nexus-slash%"
  val formRegistry = loadFormConfiguration()
  val editableEntitiyTypes = buildEditableEntityTypesFromRegistry()


  def loadFormConfiguration() = {
    val confFile = Play.application().classloader.getResourceAsStream("editor_interface_configuration.json")
    try {
      Json.parse(confFile).as[JsObject]
    } catch {
      case _: Throwable => JsObject.empty
    } finally {
      confFile.close()
    }
  }

  def buildEditableEntityTypesFromRegistry(): JsObject = {
    val res = formRegistry.value.flatMap{
      case (organization, organizationDetails) =>
        organizationDetails.as[JsObject].value.flatMap{
          case (domain, domainDetails) =>
            domainDetails.as[JsObject].value.flatMap{
              case (schema, schemaDetails) =>
                schemaDetails.as[JsObject].value.map{
                  case (version, formDetails) =>
                    Json.obj(
                      "path" -> JsString(s"$organization/$domain/$schema/$version"),
                      "label" -> (formDetails.as[JsObject] \ "label").get,
                      "editable" -> JsBoolean((formDetails.as[JsObject] \ "editable").asOpt[Boolean].getOrElse(true)))
                }
            }
        }
    }.toSeq.sortWith{case (jsO1, jsO2) => (jsO1 \ "label").as[String] < ((jsO2 \ "label").as[String])}
    Json.obj("data" -> JsArray(res))
  }

  def getFormStructure(entityType: NexusPath, data: JsValue): JsValue = {
    val nexusId = (data \ "@id").as[String]
    // retrieve form template
    val formTemplateOpt = (formRegistry \ entityType.org \ entityType.domain \ entityType.schema \ entityType.version).asOpt[JsObject]

    formTemplateOpt match {
      case Some(formTemplate) =>
        // fill template with data
        val idFields = Json.obj(
          ("id" -> Json.obj(
            ("value" -> Json.obj(
              ("path" -> entityType.toString()),
              ("nexus_id" -> JsString(nexusId)))))))

        val fields = (formTemplate \ "fields").as[JsObject].fields.foldLeft(idFields) {
          case (filledTemplate, (key, fieldContent)) =>
            if (data.as[JsObject].keys.contains(key)) {
              val newValue = (fieldContent \ "type").asOpt[String].getOrElse("") match {
                case "DropdownSelect" =>
                  fieldContent.as[JsObject] + ("value", transformToArray(key, data))
                case _ =>
                  fieldContent.as[JsObject] + ("value", (data \ key).get)
              }
              filledTemplate + (escapeSlash(key), newValue)
            } else {
              filledTemplate
            }
        }
        Json.obj("fields" -> fields) +
          ("label", (formTemplate \ "label").get) +
          ("editable", JsBoolean((formTemplate.as[JsObject] \ "editable").asOpt[Boolean].getOrElse(true))) +
          ("ui_info", (formTemplate \ "ui_info").getOrElse(JsObject.empty)) +
          ("alternatives", (data \ "http://hbp.eu/reconciled#alternatives").asOpt[JsObject].getOrElse(Json.obj()) )

      case None =>
        JsNull
    }
  }

  def escapeSlash(string: String): String = {
    string.replaceAll("/", slashEscaper)
  }

  def unescapeSlash(string: String): String = {
    string.replaceAll(slashEscaper, "/")
  }

  def transformToArray(key: String, data: JsValue): JsArray = {
    if ((data \ key \ "@list").isDefined) {
      transformID((data \ key \ "@list").as[JsArray])
    } else if ((data \ key ).validate[JsArray].isSuccess){
      transformID((data \ key ).as[JsArray])
    }else {
      if ((data \ key \ "@id").isDefined) {
        val linkToInstance = (data \ key \ "@id").as[String]
        if (linkToInstance.contains("http")){
          JsArray().+:(Json.obj("id" -> Instance.getIdfromURL(linkToInstance)))
        } else {
          JsArray()
        }
      } else {
        JsArray()
      }
    }
  }

  def transformID(jsArray: JsArray):JsArray = {
    Json.toJson(
      jsArray.value.collect{
        case el if ((el \ "@id").as[String] contains "http") =>
          Json.obj(
            "id" -> Instance.getIdfromURL((el \ "@id").as[String]))
        }
    ).as[JsArray]
  }

}
