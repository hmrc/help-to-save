package uk.gov.hmrc.helptosave.support

import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import play.api.http.Status

trait FakeRelationshipService extends BeforeAndAfterAll with ScalaFutures {
// To do: Decide whether to use stubs here or stub repo - we don't want to duplicate stubs for Selenium tests
  this: Suite =>

  val Host = "http://localhost"
  val Port = 7002
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  override def beforeAll() = {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(Host, Port)

    wireMockServer.addStubMapping(
      get(urlPathMatching("/user-info-api.*"))
        .willReturn(
          aResponse()
            .withStatus(Status.CREATED))
        .build())

    wireMockServer.addStubMapping(
      get(urlPathMatching("/help-to-save-stub/eligibilitycheck/AG010123C"))
        .willReturn(
          aResponse()
            .withStatus(Status.CREATED)
            .withBody("{\"isEligible\":true}"))
        .build())

    wireMockServer.addStubMapping(
      post(urlPathMatching("/help-to-save-stub/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK))
        .build())
  }

  override def afterAll() = {
    println("Stopping the mock backend server")
    super.afterAll()
    wireMockServer.stop()
  }

}


