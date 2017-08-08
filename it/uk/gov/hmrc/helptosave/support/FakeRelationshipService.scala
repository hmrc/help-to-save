package uk.gov.hmrc.helptosave.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait FakeRelationshipService extends BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {
// To do: Decide whether to use stubs here or stub repo - we don't want to duplicate stubs for Selenium tests
  this: Suite =>

  val Host = "http://localhost"
  val Port = 7002
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  override def beforeAll() = {
    super.beforeAll()
    WireMock.configureFor(Host, Port)
    wireMockServer.start()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetAll()
  }

  override def afterAll() = {
    println("Stopping the mock backend server")
    super.afterAll()
    wireMockServer.stop()
  }



}


