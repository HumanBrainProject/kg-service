/*
*   Copyright (c) 2020, EPFL/Human Brain Project PCO
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

package services

import helpers.ConfigMock
import helpers.ConfigMock._
import mockws.{MockWS, MockWSHelpers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.test.Injecting

import scala.concurrent.duration._

class ESServiceSpec extends PlaySpec with GuiceOneAppPerTest with MockWSHelpers with MockitoSugar with Injecting {
  override def fakeApplication() = ConfigMock.fakeApplicationConfig.build()
  implicit val scheduler = monix.execution.Scheduler.global
  "ESService#getIndices" should {

    "Get ES Indices as list" in {
      val jsonResult =
        """[{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2","uuid":"j2wUjttdQouUzxesvCZ-Rw","pri":"5","rep":"1","docs.count":"2","docs.deleted":"0","store.size":"6.3kb","pri.store.size":"6.3kb"},{"health":"yellow","status":"open","index":"kg_minds","uuid":"0LmL9S4CRn-AncN5I6SxoA","pri":"5","rep":"1","docs.count":"283","docs.deleted":"0","store.size":"3mb","pri.store.size":"3mb"},{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3","uuid":"jBtNcdpGTLO6jEVmTiXyhQ","pri":"5","rep":"1","docs.count":"4","docs.deleted":"0","store.size":"11.7kb","pri.store.size":"11.7kb"}]"""
      val expected = List(
        "nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2",
        "kg_minds",
        "nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3"
      )
      val endpoint = s"/$esHost/_cat/indices?format=json"
      val ws = MockWS {
        case ("GET", endpoint) => Action { Ok(Json.parse(jsonResult)) }
      }
      val configService = mock[ConfigurationService]
      val eSService = new ESService(ws, configService)
      val result =
        eSService.getEsIndices().runSyncUnsafe(10.seconds)
      assert(result == expected)
    }

  }
}
