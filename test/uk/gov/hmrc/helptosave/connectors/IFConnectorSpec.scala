/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.connectors

import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import uk.gov.hmrc.helptosave.util.WireMockMethods
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.http.test.ResponseMatchers
import play.api.libs.json.Json

class IFConnectorSpec
    extends TestSupport
    with MockPagerDuty
    with TestData
    with WireMockSupport
    with WireMockMethods
    with ScalaCheckDrivenPropertyChecks
    with ResponseMatchers
    with EitherValues {

  override lazy val additionalConfig: Configuration =
    Configuration(
      "microservice.services.if.host"                    -> wireMockHost,
      "microservice.services.if.port"                    -> wireMockPort,
      "microservice.services.paye-personal-details.host" -> wireMockHost,
      "microservice.services.paye-personal-details.port" -> wireMockPort
    )

  lazy val connector: IFConnector             = fakeApplication.injector.instanceOf[IFConnector]
  val originatorIdHeader: Map[String, String] = Map("Originator-Id" -> originatorIdHeaderValue)

  "the getPersonalDetails method" must {

    val nino = randomNINO()
    val url  = s"/if/pay-as-you-earn/02.00.00/individuals/$nino"

    val header = appConfig.ifHeaders ++ originatorIdHeader
    "return pay personal details for a successful nino" in {
      when(GET, url, headers = header.toMap)
        .thenReturn(200, payeDetails(nino))

      val result = await(connector.getPersonalDetails(nino))

      result.value.status shouldBe 200
    }

    "return 500 status when call to IF fails" in {
      when(GET, url, headers = header.toMap)
        .thenReturn(500, Json.toJson(Json.toJson(payeDetails(nino))))

      val result = await(connector.getPersonalDetails(nino))
      result.left.value.statusCode shouldBe 500
    }

  }

}
