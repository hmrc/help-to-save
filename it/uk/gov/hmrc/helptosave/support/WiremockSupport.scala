/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.hmrc.helptosave.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WiremockSupport extends BeforeAndAfterAll with BeforeAndAfterEach {
  // To do: Decide whether to use stubs here or stub repo - we don't want to duplicate stubs for Selenium tests
  this: Suite ⇒

  val host = "http://localhost"
  val port = 7002
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(port))

  override def beforeAll() = {
    super.beforeAll()
    WireMock.configureFor(host, port)
    wireMockServer.start()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetAll()
  }

  override def afterAll() = {
    super.afterAll()
    wireMockServer.stop()
  }



}


