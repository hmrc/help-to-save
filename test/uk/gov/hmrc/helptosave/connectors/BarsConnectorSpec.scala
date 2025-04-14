/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.bank.BankDetailsValidationRequest
import uk.gov.hmrc.helptosave.util.WireMockMethods
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.http.test.ResponseMatchers

import java.util.UUID

class BarsConnectorSpec
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
      "microservice.services.bank-account-reputation.host" -> wireMockHost,
      "microservice.services.bank-account-reputation.port" -> wireMockPort
    )

  val connector: BarsConnector = fakeApplication.injector.instanceOf[BarsConnector]

  "The BarsConnector" when {

    "validating bank details" must {

      "set headers and request body as expected and return http response to the caller" in {
        val trackingId = UUID.randomUUID()
        val headers    = Map("Content-Type" -> "application/json", "X-Tracking-Id" -> trackingId.toString)
        val body       = Json.parse(
          """{
            | "account": {
            |    "sortCode": "123456",
            |    "accountNumber": "0201234"
            |  }
            |}""".stripMargin
        )

        val response =
          """{
            |  "accountNumberIsWellFormatted": "yes",
            |  "nonStandardAccountDetailsRequiredForBacs": "no",
            |  "sortCodeIsPresentOnEISCD":"yes",
            |  "sortCodeBankName": "Lloyds",
            |  "sortCodeSupportsDirectDebit": "yes",
            |  "sortCodeSupportsDirectCredit": "yes",
            |  "iban": "GB59 HBUK 1234 5678"
            |}""".stripMargin

        when(POST, "/validate/bank-details", headers = headers, body = Some(body.toString()))
          .thenReturn(Status.OK, Json.parse(response))

        val result =
          await(connector.validate(BankDetailsValidationRequest("AE123456C", "123456", "0201234"), trackingId))
        result.value.status shouldBe 200
        result.value.body   shouldBe Json.parse(response).toString()
      }
    }
  }

}
