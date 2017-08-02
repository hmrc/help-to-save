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
            .withStatus(Status.CREATED)
              .withBody("{\"given_name\": \"firstname\"," +
                " \"family_name\" : \"surname\", " +
                " \"middle_name\" : \"middle\", " +
                " \"address\" : " +
                "{ \"address\" : \"this is an address\", " +
                " \"postcode\" : \"BN43 XXX\", " +
                " \"country\" : \"United Kingdom\", " +
                " \"countryCode\" : \"GB\" }, " +
                " \"birthdate\" : \"1997, 12, 12\", " +
                " \"nino\" : \"AG010123A\", " +
                " \"hmrc_enrolments\" : \"None\", " +
                " \"email\" : \"email@gmail.com\" }"))
        .build())

    wireMockServer.addStubMapping(
      get(urlPathMatching("/help-to-save-stub/eligibilitycheck/AG010123A"))
        .willReturn(
          aResponse()
            .withStatus(Status.CREATED)
            .withBody("{\"isEligible\":true}"))
        .build())

    wireMockServer.addStubMapping(
      post(urlPathMatching("/help-to-save-stub/oauth/token"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody("{\"client_id\" : \"AG010123A\"," +
              "\"client_secret\" : \"secret\"," +
              "\"redirect_uri\" : \"http://localhost:7000:something\"," +
              " \"code\" : \"dsdvsdvfds\"}"))
        .build())
  }

  override def afterAll() = {
    println("Stopping the mock backend server")
    super.afterAll()
    wireMockServer.stop()
  }

}


