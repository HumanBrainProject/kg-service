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

import helpers.{BlazegraphHelper, FormHelper, JsFlattener, NexusHelper}
import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.http.HttpEntity
import play.api.libs.json.{JsValue, _}
import play.api.libs.json.Reads._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import helpers.ResponseHelper._
import models.{InMemoryKnowledge, Instance, NexusPath, UserInfo}
import org.joda.time.DateTime
import org.json4s.{Diff, JsonAST}
import org.json4s.JsonAST.JNothing
import org.json4s.native.{JsonMethods, JsonParser}

import scala.collection.immutable.SortedSet
import scala.concurrent.{ExecutionContext, Future}


class InstanceController @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext, ws: WSClient, config: Configuration)
  extends AbstractController(cc) {
  val blazegraphNameSpace = config.getOptional[String]("blazegraph.namespace").getOrElse("kg")
  val nexusEndpoint = config.getOptional[String]("nexus.endpoint").getOrElse("https://nexus-dev.humanbrainproject.org")
  val reconcileEndpoint = config.getOptional[String]("reconcile.endpoint").getOrElse("https://nexus-admin-dev.humanbrainproject.org/reconcile")
  val reconciledSpace = config.getOptional[String]("nexus.reconciledspace").getOrElse("reconciled/poc")
  val manualSpace = config.getOptional[String]("nexus.manualspace").getOrElse("manual/poc")
  val sparqlEndpoint = config.getOptional[String]("blazegraph.endpoint").getOrElse("http://localhost:9999")
  val oidcUserInfoEndpoint = config.get[String]("oidc.userinfo")
  val inMemoryManualSpaceSchemas = new InMemoryKnowledge(manualSpace)
  val inMemoryReconciledSpaceSchemas = new InMemoryKnowledge(reconciledSpace)

  val logger = Logger(this.getClass)

  // TODO Check for authentication and groups as for now everybody could see the whole graph
  def list(datatype: String): Action[AnyContent] = Action.async { implicit request =>
    val nexusPath: NexusPath = NexusPath(datatype.split("/").toList)
    val sparqlPayload =
      s"""
         |SELECT DISTINCT ?instance ?name  ?description
         |WHERE {
         |  {
         |    ?instance <http://schema.org/name> ?name .
         |	?instance a <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/Instance> .
         |  ?instance <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/deprecated> false .
         |  ?instance <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/schema> <https://nexus-dev.humanbrainproject.org/v0/schemas/${nexusPath.toString()}> .
         |      OPTIONAL{ ?instance <http://schema.org/description> ?description .}
         |    FILTER(!EXISTS{?i <http://hbp.eu/reconciled#original_parent> ?instance}) .
         |    }
         |   UNION {
         |	   ?reconciled <http://schema.org/name> ?name .
         |    OPTIONAL{ ?reconciled <http://schema.org/description> ?description .}
         |    ?reconciled <http://hbp.eu/reconciled#original_parent> ?instance .
         |  	?reconciled a <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/Instance> .
         |  ?reconciled <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/deprecated> false .
         |  ?reconciled <https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/schema> <https://nexus-dev.humanbrainproject.org/v0/schemas/reconciled/poc/${nexusPath.schema}/v0.0.4> .
         |
         |  }
         |}
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
            val nexusPath = NexusPath(datatype)
            Ok(Json.obj(
              "data" -> InstanceController.formatInstanceList(arr),
              "label" -> JsString((FormHelper.formRegistry \ nexusPath.org \ nexusPath.domain \ nexusPath.schema \ nexusPath.version \ "label").asOpt[String].getOrElse(datatype)))
            )
          case _ =>
            Result(
              ResponseHeader(
                res.status,
                flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](res.headers))
              ),
              HttpEntity.Strict(res.bodyAsBytes, getContentType(res.headers))
            )
        }
    }
    res
  }

  def get(id: String): Action[AnyContent] = Action.async { implicit request =>
    val token = request.headers.get("Authorization").getOrElse("")
    val nexusPath = NexusPath(id.split("/").toList)
    retrieveOriginalInstance(id, token).flatMap[Result] {
      case Left(res) => Future.successful(Result(ResponseHeader(res.status, flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](res.headers))),
        HttpEntity.Strict(res.bodyAsBytes, getContentType(res.headers))))
      case Right(originalInstance) =>
        retrieveIncomingLinks(originalInstance, token).map {
          instances =>
            val reconcileInstances = instances.filter(instance => instance.nexusPath.toString() contains s"$reconciledSpace/${nexusPath.schema}/${nexusPath.version}")
            if (reconcileInstances.nonEmpty) {
              val reconcileInstance = reconcileInstances.head
              FormHelper.getFormStructure(originalInstance.nexusPath, reconcileInstance.content) match {
                case JsNull => NotImplemented("Form template is not yet implemented")
                case json => Ok(json)
              }
            } else {
              logger.debug("Nothing found in the consolidated space")
              FormHelper.getFormStructure(originalInstance.nexusPath, originalInstance.content) match {
                case JsNull => NotImplemented("Form template is not yet implemented")
                case json => Ok(json)
              }

            }
        }
    }
  }

  def getSpecificReconciledInstance(id:String, revision:Int): Action[AnyContent] = Action.async { implicit request =>
    val token = request.headers.get("Authorization").getOrElse("")
    val nexusPath = NexusPath(id.split("/").toList)
    ws
      .url(s"https://$nexusEndpoint/v0/data/$id?rev=$revision&deprecated=false&fields=all")
      .withHttpHeaders("Authorization" -> token).get().map{
      response => response.status match {
        case OK =>
          val json = response.json
          val nexusId = Instance.getIdfromURL((json \ "http://hbp.eu/reconciled#original_parent" \ "@id").as[String])
          val datatype = nexusId.splitAt(nexusId.lastIndexOf("/"))
          val originalDatatype = NexusPath(datatype._1.split("/").toList)
          FormHelper.getFormStructure(originalDatatype, response.json) match {
            case JsNull => NotImplemented("Form template is not yet implemented")
            case json => Ok(json)
          }
        case _ =>
          Result(
            ResponseHeader(
              response.status,
              flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](response.headers))
            ),
            HttpEntity.Strict(response.bodyAsBytes, getContentType(response.headers))
          )
      }

    }
  }


  def retrieveIncomingLinks(originalInstance: Instance,
                            token: String): Future[IndexedSeq[Instance]] = {
    val filter =
      """
        |{"op":"or","value": [{
        |   "op":"eq",
        |   "path":"https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/organization",
        |   "value": "https://nexus-dev.humanbrainproject.org/v0/organizations/reconciled"
        | }, {
        |   "op":"eq",
        |   "path":"https://nexus-dev.humanbrainproject.org/vocabs/nexus/core/terms/v0.1.0/organization",
        |   "value": "https://nexus-dev.humanbrainproject.org/v0/organizations/manual"
        | }
        | ]
        |}
      """.stripMargin.stripLineEnd.replaceAll("\r\n", "")
    NexusHelper.listAllNexusResult(s"$nexusEndpoint/v0/data/${originalInstance.id()}/incoming?deprecated=false&fields=all&size=50&filter=$filter", token).map {
      incomingLinks =>
        incomingLinks.map(el => Instance((el \ "source").as[JsValue])).toIndexedSeq
    }
  }


  def generateReconciledInstance(reconciledInstance: Instance, manualUpdates: IndexedSeq[Instance], manualEntityToBestored: JsObject, userInfo: UserInfo, parentRevision: Int, parentId: String, token: String): JsObject = {

    val recInstanceWithParents = addManualUpdateLinksToReconcileInstance(reconciledInstance, manualUpdates, manualEntityToBestored)
    val cleanUpObject = InstanceController.cleanUpInstanceForSave(recInstanceWithParents).+("@type" -> JsString(s"http://hbp.eu/reconciled#${reconciledInstance.nexusPath.schema.capitalize}"))
    cleanUpObject +
      ("http://hbp.eu/reconciled#updater_id", JsString(userInfo.id)) +
      ("http://hbp.eu/reconciled#original_rev", JsNumber(parentRevision)) +
      ("http://hbp.eu/reconciled#original_parent", Json.obj("@id" -> JsString(parentId))) +
      ("http://hbp.eu/reconciled#origin", JsString(parentId)) +
      ("http://hbp.eu/reconciled#update_timestamp", JsNumber(new DateTime().getMillis))
  }

  def createReconcileInstance(instance: JsObject, schema: String, version: String, token: String): Future[WSResponse] = {
    ws.url(s"$nexusEndpoint/v0/data/$reconciledSpace/${schema}/${version}").withHttpHeaders("Authorization" -> token).post(instance)
  }

  def updateReconcileInstance(instance: JsObject, nexusPath: NexusPath, id: String, revision: Int, token: String): Future[WSResponse] = {
    ws.url(s"$nexusEndpoint/v0/data/$reconciledSpace/${nexusPath.schema}/${nexusPath.version}/$id?rev=${revision}").withHttpHeaders("Authorization" -> token).put(instance)
  }

  def addManualUpdateLinksToReconcileInstance(reconciledInstance: Instance, incomingLinks: IndexedSeq[Instance], manualEntityToBeStored: JsObject): Instance = {
    val manualUpdates: IndexedSeq[Instance] = incomingLinks.filter(instance => instance.nexusPath.toString() contains manualSpace)
    val currentParent = (reconciledInstance.content \ "http://hbp.eu/reconciled#parents").asOpt[List[JsValue]].getOrElse(List[JsValue]())
    val updatedParents: List[JsValue] = manualUpdates.foldLeft(currentParent) { (acc, manual) =>
      val manualId = Json.obj("@id" -> (manual.content \ "@id").as[String])
      manualId :: acc
    }
    val jsArray = Json.toJson(updatedParents)
    val currentUpdater = (manualEntityToBeStored \ "http://hbp.eu/manual#updater_id").as[String]
    val altJson = InstanceController.generateAlternatives(
      manualUpdates.map(InstanceController.cleanUpInstanceForSave)
      .filter(js => (js \ "http://hbp.eu/manual#updater_id").as[String] != currentUpdater).+:(manualEntityToBeStored)
    )
    val res = reconciledInstance.content.+("http://hbp.eu/reconciled#parents" -> jsArray).+("http://hbp.eu/reconciled#alternatives", altJson)
    Instance(reconciledInstance.nexusUUID, reconciledInstance.nexusPath, res)
  }




  type UpdateInfo = (String, Int, String)

  def consolidateFromManualSpace(
                                  originalInstance: Instance,
                                  incomingLinks: IndexedSeq[Instance],
                                  updateToBeStoredInManual: JsObject
                                ): (Instance, Option[IndexedSeq[UpdateInfo]]) = {
    val manualUpdates = incomingLinks.filter(instance => instance.nexusPath.toString() contains manualSpace)
    logger.debug(s"Result from incoming links $manualUpdates")
    if (manualUpdates.nonEmpty) {
      val manualUpdateDetails = manualUpdates.map(manualEntity => manualEntity.extractUpdateInfo())
      //Call reconcile API
      val originJson = originalInstance.content
      val updatesByPriority = InstanceController.buildManualUpdatesFieldsFrequency(manualUpdates, updateToBeStoredInManual)
      //TODO Check the result is correct this works
      val result = Instance(InstanceController.reconcilationLogic(updatesByPriority, originJson))
      logger.debug(s"Reconciled instance $result")
      (result, Some(manualUpdateDetails))
    } else {
      val consolidatedInstance = buildInstanceFromForm(originalInstance.content, updateToBeStoredInManual)
      (Instance(consolidatedInstance), None)
    }
  }

  def retrieveOriginalInstance(id: String, token: String): Future[Either[WSResponse, Instance]] = {
    ws.url(s"$nexusEndpoint/v0/data/$id?fields=all").addHttpHeaders("Authorization" -> token).get().map {
      res =>
        res.status match {
          case OK =>
            val json = res.json
            // Get data from manual space
            Right(Instance(json))
          // Get Instance through filter -> incoming link filter by space
          case _ =>
            logger.error(s"Error: Could not fetch original instance - ${res.body}")
            Left(res)
        }
    }

  }

  def buildDiffEntity(consolidatedResponse: Instance, newValue: String, originalInstance: Instance): JsObject = {
    val consolidatedJson = JsonParser.parse(InstanceController.cleanInstanceManual(consolidatedResponse.content).toString())
    val newJson = JsonParser.parse(newValue)
    val Diff(changed, added, deleted) = consolidatedJson.diff(newJson)
    val diff: JsonAST.JValue = (deleted, changed) match {
      case (JsonAST.JNothing, JsonAST.JNothing) =>
        consolidatedJson
      case (JsonAST.JNothing, _) =>
        changed.merge(added)
      case _ =>
        /* fields deletion. Deletion are allowed on manual space ONLY
         * final diff is then an addition/update from original view
         * this allows partially defined form (some field are then not updatable)
         */
        val Diff(changedFromOrg, addedFromOrg, deletedFromOrg) = JsonParser.parse(originalInstance.content.toString()).diff(newJson)
        if (deletedFromOrg != JsonAST.JNothing) {
          println(s"""PARTIAL FORM DEFINITION - missing fields from form: ${deletedFromOrg.toString}""")
        }
        changedFromOrg.merge(addedFromOrg)
    }

    Json.parse(
      JsonMethods.compact(JsonMethods.render(diff))).as[JsObject] +
      ("http://hbp.eu/manual#origin", JsString((originalInstance.content \ "links" \ "self").as[String].split("/").last)) +
      ("http://hbp.eu/manual#parent", Json.obj("@id" -> (originalInstance.content \ "links" \ "self").get.as[JsString]))
  }

  def buildInstanceFromForm(original: JsObject, formContent: JsObject): JsObject = {
    val flattened = JsFlattener(formContent)
    InstanceController.applyChanges(original, flattened)
  }

  def getTokenFromRequest(request: Request[AnyContent]): String = {
    request.headers.toMap.getOrElse("Authorization", Seq("")).head
  }

  def update(id: String): Action[AnyContent] = Action.async { implicit request =>
    val token = request.headers.get("Authorization").getOrElse("")
    //    val orgContentFuture = retrieve(id, request.headers) // call core of get id
    val newValue = request.body.asJson.get
    retrieveOriginalInstance(id, token).flatMap[Result] {
      case Left(res) => Future.successful(Result(ResponseHeader(res.status, flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](res.headers))),
        HttpEntity.Strict(res.bodyAsBytes, getContentType(res.headers))))
      case Right(originalInstance) =>
        retrieveIncomingLinks(originalInstance, token).flatMap[Result] {
          incomingLinks =>
            val currentReconciledInstances = incomingLinks.filter(instance => instance.nexusPath.toString() contains reconciledSpace)
            val currentInstanceDisplayed = InstanceController.getCurrentInstanceDisplayed(currentReconciledInstances, originalInstance)

            // As we cannot pass / in the name of a field we have replaced them with %nexus-slash%
            val updateFromUI = Json.parse(FormHelper.unescapeSlash(newValue.toString())).as[JsObject] - "id"
            val updatedInstance = buildInstanceFromForm(originalInstance.content, updateFromUI)
            val updateToBeStoredInManual = buildDiffEntity(currentInstanceDisplayed, updatedInstance.toString, originalInstance) + ("@type", JsString(s"http://hbp.eu/manual#${originalInstance.nexusPath.schema.capitalize}"))
            val userInfoFuture = getUserInfo(getTokenFromRequest(request))
            consolidateFromManualSpace(originalInstance, incomingLinks, updateToBeStoredInManual) match {
              case (consolidatedInstance, manualEntitiesDetailsOpt) =>
                logger.debug(s"Consolidated instance $updatedInstance")
                val re: Future[Result] = for {
                  createdSchemas <- createManualSchemaIfNeeded(updateToBeStoredInManual, originalInstance, token, inMemoryManualSpaceSchemas, manualSpace, "manual")
                  userInfo <- userInfoFuture
                  preppedEntityForStorage = InstanceController.prepareManualEntityForStorage(updateToBeStoredInManual, userInfo)
                  createdInManualSpace <- upsertUpdateInManualSpace(manualEntitiesDetailsOpt, userInfo, originalInstance.nexusPath.schema, preppedEntityForStorage, token)
                  createReconciledInstance <- upsertReconciledInstance(incomingLinks, originalInstance, preppedEntityForStorage, updatedInstance, consolidatedInstance, token, userInfo)
                } yield {
                  logger.debug(s"Result from reconciled upsert: ${createReconciledInstance.status}")
                  logger.debug(createReconciledInstance.body)
                  (createReconciledInstance.status, createdInManualSpace.status) match {
                    case (OK, OK) => Ok(InstanceController.formatFromNexusToOption(updatedInstance))
                    case (OK, _) => Result(
                      ResponseHeader(
                        createdInManualSpace.status,
                        flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](createdInManualSpace.headers))
                      ),
                      HttpEntity.Strict(createdInManualSpace.bodyAsBytes, getContentType(createdInManualSpace.headers))
                    )
                    case (_, _) =>
                      Result(
                        ResponseHeader(
                          createReconciledInstance.status,
                          flattenHeaders(filterContentTypeAndLengthFromHeaders[Seq[String]](createReconciledInstance.headers))
                        ),
                        HttpEntity.Strict(createReconciledInstance.bodyAsBytes, getContentType(createReconciledInstance.headers))
                      )
                  }
                }
                re
            }
        }
    }
  }

  def upsertReconciledInstance(instances: IndexedSeq[Instance], originalInstance: Instance,
                               manualEntity: JsObject, updatedValue: JsObject,
                               consolidatedInstance: Instance, token: String,
                               userInfo: UserInfo): Future[WSResponse] = {

    val reconcileInstances = instances.filter(instance => instance.nexusPath.toString() contains reconciledSpace)
    val parentId = (originalInstance.content \ "@id").as[String]
    if (reconcileInstances.nonEmpty) {
      val reconcileInstance = reconcileInstances.head
      val parentRevision = (reconcileInstance.content \ "nxv:rev").as[Int]
      val payload = generateReconciledInstance(Instance(updatedValue), instances, manualEntity, userInfo, parentRevision, parentId, token)
      updateReconcileInstance(payload, reconcileInstance.nexusPath, reconcileInstance.nexusUUID, parentRevision, token)
    } else {
      val parentRevision = (originalInstance.content \ "nxv:rev").as[Int]
      val payload = generateReconciledInstance(consolidatedInstance, instances, manualEntity, userInfo, parentRevision, parentId, token)
      createManualSchemaIfNeeded(updatedValue, originalInstance, token, inMemoryReconciledSpaceSchemas, reconciledSpace, "reconciled").flatMap {
        res =>
          createReconcileInstance(payload, consolidatedInstance.nexusPath.schema, consolidatedInstance.nexusPath.version, token)
      }
    }
  }

  def createManualSchemaIfNeeded(manualEntity: JsObject, originalInstance: Instance, token: String, schemasHashMap: InMemoryKnowledge, space: String, destinationOrg: String): Future[Boolean] = {
    // ensure schema related to manual update exists or create it
    if (manualEntity != JsNull) {
      if (schemasHashMap.manualSchema.isEmpty) { // initial load
        schemasHashMap.loadManualSchemaList(token)
      }
      if (!schemasHashMap.manualSchema.contains(s"$nexusEndpoint/v0/schemas/$space/${originalInstance.nexusPath.schema}/${originalInstance.nexusPath.version}")) {
        NexusHelper.createSchema(destinationOrg, originalInstance.nexusPath.schema.capitalize, space, originalInstance.nexusPath.version, token).map {
          response =>
            response.status match {
              case OK => schemasHashMap.loadManualSchemaList(token)
                logger.info(s"Schema created properly for : " +
                  s"$space/${originalInstance.nexusPath.schema}/${originalInstance.nexusPath.version}")
                Future.successful(true)
              case _ => logger.error(s"ERROR - schema does not exist and " +
                s"automatic creation failed - ${response.body}")
                Future.successful(false)
            }
        }

      }
    }
    Future.successful(true)
  }

  def upsertUpdateInManualSpace(manualEntitiesDetailsOpt: Option[IndexedSeq[UpdateInfo]], userInfo: UserInfo, schema: String, manualEntity: JsObject, token: String): Future[WSResponse] = {
    manualEntitiesDetailsOpt.flatMap { manualEntitiesDetails =>
      // find manual entry corresponding to the user
      manualEntitiesDetails.filter(_._3 == userInfo.id).headOption.map {
        case (manualEntityId, manualEntityRevision, _) =>
          ws.url(s"$nexusEndpoint/v0/data/${Instance.getIdfromURL(manualEntityId)}/?rev=$manualEntityRevision").addHttpHeaders("Authorization" -> token).put(
            manualEntity
          )
      }
    }.getOrElse {
      ws.url(s"$nexusEndpoint/v0/data/$manualSpace/${schema}/v0.0.4").addHttpHeaders("Authorization" -> token).post(
        manualEntity + ("http://hbp.eu/manual#updater_id", JsString(userInfo.id))
      )
    }
  }


  def getUserInfo(token: String): Future[UserInfo] = {
    ws.url(oidcUserInfoEndpoint).addHttpHeaders("Authorization" -> token).get().map {
      res =>
        UserInfo(res.json.as[JsObject])
    }
  }

}

object InstanceController {

  def buildManualUpdatesFieldsFrequency(manualUpdates: IndexedSeq[Instance], currentUpdate: JsObject): Map[String, SortedSet[(JsValue, Int)]] = {
    val cleanMap: IndexedSeq[Map[String, JsValue]] = currentUpdate.as[Map[String, JsValue]] +: cleanListManualData(manualUpdates)
    buildMapOfSortedManualUpdates(cleanMap)
  }

  // build reconciled view from updates statistics
  def reconcilationLogic(frequencies: Map[String, SortedSet[(JsValue, Int)]], origin: JsObject): JsObject = {
    // simple logic: keep the most frequent
    val transformations = frequencies.map {
      case (pathString, freqs) => (JsFlattener.buildJsPathFromString(pathString), freqs.last._1)
    }.toSeq
    applyChanges(origin, transformations)
  }

  // apply a set of transformation to an instance
  def applyChanges(instance: JsObject, changes: Seq[(JsPath, JsValue)]): JsObject = {
    val obj = changes.foldLeft(instance) {
      case (res, (path, value)) =>
        val updatedJson = updateJson(res, path, value)
        updatedJson
    }
    obj
  }

  // return an updated JsObject or the src object in case of failure
  def updateJson(instance: JsObject, path: JsPath, newValue: JsValue): JsObject = {
    val simpleUpdateTransformer = path.json.update(
      of[JsValue].map { _ => newValue }
    )
    val res = instance.transform(simpleUpdateTransformer).getOrElse(instance)
    res
  }

  def buildMapOfSortedManualUpdates(manualUpdates: IndexedSeq[Map[String, JsValue]]): Map[String, SortedSet[(JsValue, Int)]] = {
    implicit val order = Ordering.fromLessThan[(JsValue, Int)](_._2 < _._2)
    // For each update
    val tempMap = manualUpdates.foldLeft(Map.empty[String, List[JsValue]]) {
      case (merged, m) =>
        val tempRes: Map[String, List[JsValue]] = m.foldLeft(merged) { case (acc, (k, v)) =>
          acc.get(k) match {
            case Some(existing) => acc.updated(k, v :: existing)
            case None => acc.updated(k, List(v))
          }
        }
        tempRes
    }
    val sortedSet = tempMap
      .filter(e => e._1 != "@type" &&
        e._1 != "http://hbp.eu/manual#parent" &&
        e._1 != "http://hbp.eu/manual#origin" &&
        e._1 != "http://hbp.eu/manual#updater_id")
      .map { el =>
        val e = el._2.groupBy(identity).mapValues(_.size)
        el._1 -> SortedSet(e.toList: _*)
    }
    sortedSet
  }


  def merge[K, V](maps: Seq[Map[K, V]])(f: (K, V, V) => V): Map[K, V] = {
    maps.foldLeft(Map.empty[K, V]) { case (merged, m) =>
      m.foldLeft(merged) { case (acc, (k, v)) =>
        acc.get(k) match {
          case Some(existing) => acc.updated(k, f(k, existing, v))
          case None => acc.updated(k, v)
        }
      }
    }
  }

  def cleanListManualData(manualUpdates: IndexedSeq[Instance]): IndexedSeq[Map[String, JsValue]] = {
    manualUpdates.map(el => cleanManualDataFromNexus(el.content))
  }

  def cleanManualDataFromNexus(jsObject: JsObject): Map[String, JsValue] = {
    cleanManualData(jsObject).fields.toMap
  }

  def cleanManualData(jsObject: JsObject): JsObject = {
    jsObject.-("@id").-("@type").-("links").-("nxv:rev").-("nxv:deprecated")
  }

  def cleanUpInstanceForSave(instance: Instance): JsObject = {
    val jsonObj = instance.content
    jsonObj - ("@context") - ("@type") - ("@id") - ("nxv:rev") - ("nxv:deprecated") - ("links")
  }

  def prepareManualEntityForStorage(manualEntity: JsObject, userInfo: UserInfo): JsObject = {
    manualEntity.+("http://hbp.eu/manual#updater_id", JsString(userInfo.id))
      .+("http://hbp.eu/manual#update_timestamp", JsNumber(new DateTime().getMillis))
      .-("@context")
      .-("@id")
      .-("links")
      .-("nxv:rev")
      .-("nxv:deprecated")
  }


  // NOTE
  /*
      call to reconcile service api may not be used anymore since all transorfmation happen on fully qualify instances
   */

  def formatForReconcile(manualUpdates: IndexedSeq[JsValue], originJson: JsValue): (JsValue, JsValue) = {
    val allResults = manualUpdates.map((jsonResult) => (jsonResult \ "source").as[JsValue]).+:(originJson)
    val contents = allResults.foldLeft(Json.arr())(
      (array, jsonResult) =>
        array.+:(toReconcileFormat(jsonResult, (jsonResult \ "@id").as[String]))
    )
    val priorities: JsValue = allResults.foldLeft(Json.obj())(
      (jsonObj, jsResult) =>
        jsonObj + ((jsResult \ "@id").as[String], Json.toJson(getPriority((jsResult \ "@id").as[String])))
    )
    (contents, priorities)
  }


  def cleanInstanceManual(jsObject: JsObject): JsObject = {
    jsObject.-("http://hbp.eu/manual#parent")
      .-("http://hbp.eu/manual#origin")
      .-("http://hbp.eu/manual#updater_id")
  }

  def formatInstanceList(jsArray: JsArray): JsValue = {

    val arr = jsArray.value.map { el =>
      val id = (el \ "instance" \ "value").as[String]
      val name = (el \ "name" \ "value").as[JsString]
      val description: JsString = if ((el \ "description").isDefined) {
        (el \ "description" \ "value").as[JsString]
      } else {
        JsString("")
      }
      Json.obj("id" -> Instance.getIdfromURL(id), "description" -> description, "label" -> name)
    }
    Json.toJson(arr)
  }

  def formatFromNexusToOption(jsObject: JsObject): JsObject = {
    val id = (jsObject \ "@id").as[String]
    val name = (jsObject \ "http://schema.org/name").as[JsString]
    val description: JsString = if ((jsObject \ "http://schema.org/description").isDefined) {
      (jsObject \ "http://schema.org/description").as[JsString]
    } else { JsString("") }
    Json.obj("id" -> Instance.getIdfromURL(id), "description" -> description, "label" -> name)
  }

  def toReconcileFormat(jsValue: JsValue, privateSpace: String): JsObject = {
    Json.obj("src" -> privateSpace, "content" -> jsValue.as[JsObject].-("@context"))
  }

  //TODO make it dynamic :D
  def getPriority(id: String): Int = {
    if (id contains "manual/poc") {
      3
    } else {
      1
    }
  }

  def getCurrentInstanceDisplayed(currentReconciledInstances: Seq[Instance], originalInstance: Instance): Instance = {
    if (currentReconciledInstances.nonEmpty) {
      val sorted = currentReconciledInstances.sortWith { (left, right) =>
        (left.content \ "http://hbp.eu/reconciled#update_timestamp").as[Long] >
          (right.content \ "http://hbp.eu/reconciled#update_timestamp").as[Long]
      }
      sorted.head
    } else{
      originalInstance
    }
  }

  def generateAlternatives(manualUpdates: IndexedSeq[JsObject]): JsValue = {
    // Alternatives are added per user
    // So for each key we have a list of object containing the user id and the value
    val alternatives: Map[String, Seq[(String, JsValue)]] = manualUpdates
      .map { instance =>
        val dataMap: Map[String, JsValue] = instance
          .-("http://hbp.eu/manual#parent")
          .-("http://hbp.eu/manual#origin").as[Map[String, JsValue]]
        val userId: String = dataMap("http://hbp.eu/manual#updater_id").as[String]
        dataMap.map { case (k, v) => k -> ( userId, v) }
      }.foldLeft(Map.empty[String, Seq[(String, JsValue)]]) { case (map, instance) =>
      //Grouping per field in order to have a map with the field and the list of different alternatives on this field
      val tmp: List[(String, Seq[(String, JsValue)])] = instance.map { case (k, v) => (k, Seq(v)) }.toList ++ map.toList
      tmp.groupBy(_._1).map { case (k, v) => k -> v.flatMap(_._2) }
    }

    val perValue = alternatives.map{
      case (k,v) =>
        val tempMap = v.foldLeft(Map.empty[JsValue, Seq[String]]){case (map, tuple) =>
          val temp: Seq[String] = map.getOrElse(tuple._2, Seq.empty[String])
            map.updated(tuple._2, tuple._1 +: temp)
        }
        k ->  tempMap.toList.sortWith( (el1, el2) => el1._2.length > el2._2.length).map( el =>
          Json.obj("value" -> el._1, "updater_id" -> el._2)
        )
    }
    Json.toJson(
      perValue.-("@type")
        .-("http://hbp.eu/manual#update_timestamp")
        .-("http://hbp.eu/manual#updater_id")
    )
  }



}

