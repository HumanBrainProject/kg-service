package helpers

import mockws.MockWS
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.Injecting
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import common.helpers.ESHelper

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  *
  * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
  */
class ESHelperSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
  override def fakeApplication() = GuiceApplicationBuilder().configure("play.http.filters" -> "play.api.http.NoHttpFilters").build()

  "ESHelper#filterNexusGroup" should {

    "return groups without prefix and suffix" in {
      val origin = List("hbp-group1-admin", "nexus-group2", "other-group3")
      val expected = List("nexus-group2")
      val res = ESHelper.filterNexusGroups(origin, s => s)
      assert(res == expected)
    }

    "Get ES Indices as list" in {
      implicit val config = app.injector.instanceOf[Configuration]
      implicit val ec = app.injector.instanceOf[ExecutionContext]
      val jsonResult = """[{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2","uuid":"j2wUjttdQouUzxesvCZ-Rw","pri":"5","rep":"1","docs.count":"2","docs.deleted":"0","store.size":"6.3kb","pri.store.size":"6.3kb"},{"health":"yellow","status":"open","index":"kg_minds","uuid":"0LmL9S4CRn-AncN5I6SxoA","pri":"5","rep":"1","docs.count":"283","docs.deleted":"0","store.size":"3mb","pri.store.size":"3mb"},{"health":"yellow","status":"open","index":"nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3","uuid":"jBtNcdpGTLO6jEVmTiXyhQ","pri":"5","rep":"1","docs.count":"4","docs.deleted":"0","store.size":"11.7kb","pri.store.size":"11.7kb"}]"""
      val expected = List("nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup2", "kg_minds","nexus_iam_realms%2fhbp%2fgroups%2fnexus-testgroup3")
      val ws = MockWS {
        case ("GET", "/eshost/_cat/indices?format=json") => Action { Ok(Json.parse(jsonResult)) }
      }

      val result = Await.result(
        ESHelper.getEsIndices("/eshost")(ws, ec), 10.seconds
      )
      assert(result  == expected)
    }

  }
}
