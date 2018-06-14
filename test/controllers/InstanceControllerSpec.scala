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

import mockws.{MockWS, MockWSHelpers}
import org.scalatest.Matchers._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.libs.json._
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class InstanceControllerSpec extends PlaySpec with GuiceOneAppPerSuite with MockWSHelpers with Injecting {
  import helpers.ConfigMock._

  override def fakeApplication(): Application = fakeApplicationConfig.build()

  "Retrieve from manual" should {
    "build the frequency of fields in manual" in {
//      implicit val order = Ordering.fromLessThan[(JsValue, Int)]( _._2 > _._2)
//      val manualUpdates: IndexedSeq[JsValue] = Json.arr(Json.obj("source" -> Json.obj("name" -> "Bill", "desc" -> "Bill is cool")), Json.obj("source" -> Json.obj( "name" -> "Billy", "desc" -> "Billy is cooler")), Json.obj("source" -> Json.obj( "name" -> "Bill", "desc" -> "Billy is cooler"))).value
//      val expectedResult: Map[String, SortedSet[(JsValue, Int)]] = Map( "name" -> SortedSet(JsString("Billy").as[JsValue] -> 2 , JsString("Bill").as[JsValue] -> 1), "desc" -> SortedSet(JsString( "Billy is cooler").as[JsValue] -> 2, JsString("Bill is cool").as[JsValue] -> 1))
//      val result = Instance.buildManualUpdatesFieldsFrequency(manualUpdates)
//      val keyDiff = (expectedResult.keySet -- result.keySet) ++ (result.keySet -- expectedResult.keySet)
//      assert(keyDiff.isEmpty)
//      forAll(result.toList){
//        el => expectedResult(el._1) should equal (el._2)
//      }
    }
  }
  "InstanceController#list" should {
    "return a 200 with a correctly formatted result" in {
      val datatype = "data/core/datatype/v0.0.4"
      val nexusBase = "http://nexus.org/v0/data/"
      import scala.concurrent.ExecutionContext.Implicits._
      val instances = Json.arr(
        Json.obj("instance"->  Json.obj("value" -> s"$nexusBase/$datatype/123"), "name" -> Json.obj("value" -> "dataname1")),
        Json.obj("instance"-> Json.obj("value" -> s"$nexusBase/$datatype/321"), "name" -> Json.obj("value" ->"dataname2"))
      )
      val jsonResp = Json.obj("results" -> Json.obj("bindings" -> instances))
      val fakeEndpoint = s"$sparqlEndpoint/bigdata/namespace/$blazegraphNameSpace/sparql"
      implicit val ws = MockWS {
        case (GET, fakeEndpoint ) => Action { Ok(jsonResp)}
      }
      val mockCC = stubControllerComponents()
      val ec = global
      val controller = new InstanceController(mockCC)(ec, ws, fakeApplication().configuration)
      val res = contentAsString(controller.list(datatype).apply(FakeRequest()))
      res mustBe """{"data":[{"id":"/data/core/datatype/v0.0.4/123","description":"","label":"dataname1"},{"id":"/data/core/datatype/v0.0.4/321","description":"","label":"dataname2"}],"label":"data/core/datatype/v0.0.4"}"""

    }
  }

}
