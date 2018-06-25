package common.services


import common.helpers.ConfigMock
import common.helpers.ConfigMock._
import mockws.MockWS
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Injecting
import services.ESService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}



class ESServiceSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
  override def fakeApplication() = ConfigMock.fakeApplicationConfig.build()

  "ESService#getIndices" should {

    "Get ES Indices as list" in {
      implicit val config = app.injector.instanceOf[Configuration]
      implicit val ec = app.injector.instanceOf[ExecutionContext]
      val jsonResult = """[{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2","uuid":"j2wUjttdQouUzxesvCZ-Rw","pri":"5","rep":"1","docs.count":"2","docs.deleted":"0","store.size":"6.3kb","pri.store.size":"6.3kb"},{"health":"yellow","status":"open","index":"kg_minds","uuid":"0LmL9S4CRn-AncN5I6SxoA","pri":"5","rep":"1","docs.count":"283","docs.deleted":"0","store.size":"3mb","pri.store.size":"3mb"},{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3","uuid":"jBtNcdpGTLO6jEVmTiXyhQ","pri":"5","rep":"1","docs.count":"4","docs.deleted":"0","store.size":"11.7kb","pri.store.size":"11.7kb"}]"""
      val expected = List("nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2", "kg_minds","nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3")
      val endpoint = s"/$esHost/_cat/indices?format=json"
      val ws = MockWS {
        case ("GET", endpoint) => Action { Ok(Json.parse(jsonResult)) }
      }
      val eSService = new ESService(ws, config)
      val result = Await.result(
        eSService.getEsIndices(), 10.seconds
      )
      assert(result  == expected)
    }

  }
}
