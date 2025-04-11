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

package uk.gov.hmrc.helptosave.services

import org.mockito.ArgumentMatchers.{eq => eqTo, any}
import org.mockito.Mockito.{doNothing, when}
import org.mockito.stubbing.OngoingStubbing
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.BarsConnector
import uk.gov.hmrc.helptosave.models.{BARSCheck, BankDetailsValidationRequest, BankDetailsValidationResult}
import uk.gov.hmrc.helptosave.util.{NINO, UnitSpec, toFuture}
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future

class BarsServiceSpec extends UnitSpec with TestSupport with MockPagerDuty {

  private val mockBarsConnector: BarsConnector = mock[BarsConnector]

  val mockAuditor: HTSAuditor = mock[HTSAuditor]
  val returnHeaders: Map[String,Seq[String]] = Map[String, Seq[String]]()
  def mockBarsConnector(barsRequest: BankDetailsValidationRequest)(response: HttpResponse): OngoingStubbing[Future[Either[UpstreamErrorResponse, HttpResponse]]] = {
    when(mockBarsConnector
      .validate(eqTo(barsRequest), any())(any(), any()))
      .thenReturn(toFuture(Right(response)))
  }

  def mockAuditBarsEvent(expectedEvent: BARSCheck, nino: NINO): Unit = {
    doNothing().when(mockAuditor).sendEvent(eqTo(expectedEvent), eqTo(nino))(any())
  }

  val service = new BarsServiceImpl(mockBarsConnector, mockMetrics, mockPagerDuty, mockAuditor)

  "The BarsService" when {

    "validating bank details" must {

      val nino = "NINO"
      val barsRequest = BankDetailsValidationRequest(nino, "123456", "accountNumber")
      implicit val request: Request[JsValue] =
        FakeRequest("GET", "/validate-bank-details").withBody(Json.toJson(barsRequest))
      val path = request.uri

      def newResponse(accountNumberWithSortCodeIsValid: String, sortCodeIsPresentOnEISCD: String): String =
        s"""{
           |  "accountNumberIsWellFormatted": "$accountNumberWithSortCodeIsValid",
           |  "nonStandardAccountDetailsRequiredForBacs": "no",
           |  "sortCodeIsPresentOnEISCD":"$sortCodeIsPresentOnEISCD",
           |  "sortCodeBankName": "Lloyds",
           |  "sortCodeSupportsDirectDebit": "yes",
           |  "sortCodeSupportsDirectCredit": "yes",
           |  "iban": "GB59 HBUK 1234 5678"
           |}""".stripMargin

      "handle the case when the bank details are valid and the sort code exists" in {
        val response = newResponse("yes", "yes")

          mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(isValid = true, sortCodeExists = true))
      }

      "handle the case when the bank details are not valid" in {
        val response = newResponse("no", "no")

          mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(isValid = false, sortCodeExists = false))
      }

      "handle the case when the bank details are valid but the sort code does not exist" in {
        val response = newResponse("yes", "no")

          mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(isValid = true, sortCodeExists = false))
      }

      "handle the case when the bank details are indeterminate" in {
        val response = newResponse("indeterminate", "no")

          mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(isValid = true, sortCodeExists = false))
      }

      "handle the case when the bank details are valid but the sort code response cannot be parsed" in {
        val response = newResponse("yes", "blah")

          mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        val result = await(service.validate(barsRequest))
        result.isLeft shouldBe true
      }

      "handle 200 response but missing json field (accountNumberWithSortCodeIsValid)" in {
        val response =
          """{
            |  "nonStandardAccountDetailsRequiredForBacs": "no",
            |  "sortCodeIsPresentOnEISCD":"yes",
            |  "supportsBACS":"yes",
            |  "ddiVoucherFlag":"no",
            |  "directDebitsDisallowed": "yes",
            |  "directDebitInstructionsDisallowed": "yes"
            |}""".stripMargin

        mockBarsConnector(barsRequest)((HttpResponse(200, Json.parse(response), returnHeaders)))
        mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        mockPagerDutyAlert("error parsing the response json from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("error parsing the response json from bars check")
      }

      "handle unsuccessful response from bars check" in {
        mockBarsConnector(barsRequest)((HttpResponse(400, "")))
        mockPagerDutyAlert("unexpected status from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("unexpected status from bars check")
      }

      "recover from unexpected errors" in {
        when(mockBarsConnector.validate(eqTo(barsRequest), any())(any(), any()))
          .thenReturn(toFuture(Left(UpstreamErrorResponse("", 500))))

        mockPagerDutyAlert("unexpected error from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("unexpected error from bars check")
      }

    }

  }
}
