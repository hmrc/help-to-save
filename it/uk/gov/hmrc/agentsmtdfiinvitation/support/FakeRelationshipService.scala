package uk.gov.hmrc.agentsmtdfiinvitation.support

import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import play.api.http.Status

trait FakeRelationshipService extends BeforeAndAfterAll with ScalaFutures {

  this: Suite =>

  val Host = "http://localhost"
  val Port = 9427
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  override def beforeAll() = {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(Host, Port)

    wireMockServer.addStubMapping(
      post(urlPathMatching("/agents-mtdfi-relationship-stub/relationships"))
        .willReturn(
          aResponse()
            .withStatus(Status.CREATED))
        .build())
  }

  override def afterAll() = {
    println("Stopping the mock backend server")
    super.afterAll()
    wireMockServer.stop()
  }

}


