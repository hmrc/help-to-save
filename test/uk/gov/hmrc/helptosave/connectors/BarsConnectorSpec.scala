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

import java.util.UUID

import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.BankDetailsValidationRequest
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.HttpResponse

class BarsConnectorSpec extends TestSupport with HttpSupport {

  val connector = new BarsConnectorImpl(mockHttp)

  "The BarsConnector" when {

    "validating bank details" must {

      "set headers and request body as expected and return http response to the caller" in {
        val trackingId = UUID.randomUUID()
        val headers = Map("Content-Type" -> "application/json", "X-Tracking-Id" -> trackingId.toString)
        val body = Json.parse(
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

        mockPost("http://localhost:7002/validate/bank-details", headers, body)(
          Some(HttpResponse(Status.OK, Json.parse(response), Map[String, Seq[String]]())))
        val result =
          await(connector.validate(BankDetailsValidationRequest("AE123456C", "123456", "0201234"), trackingId))

        result.status shouldBe 200
        result.json shouldBe Json.parse(response)
      }
    }
  }

}
