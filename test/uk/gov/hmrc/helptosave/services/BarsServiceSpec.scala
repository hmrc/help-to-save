/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import org.scalamock.handlers.CallHandler4
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.BarsConnector
import uk.gov.hmrc.helptosave.models.{BARSCheck, BankDetailsValidationRequest, BankDetailsValidationResult, HTSEvent}
import uk.gov.hmrc.helptosave.util.{NINO, UnitSpec}
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class BarsServiceSpec extends UnitSpec with TestSupport with MockPagerDuty {

  private val mockBarsConnector: BarsConnector = mock[BarsConnector]

  val mockAuditor = mock[HTSAuditor]

  def mockBarsConnector(barsRequest: BankDetailsValidationRequest)(response: Option[HttpResponse]): CallHandler4[BankDetailsValidationRequest, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (mockBarsConnector.validate(_: BankDetailsValidationRequest, _: UUID)(_: HeaderCarrier, _: ExecutionContext)).expects(barsRequest, *, *, *)
      .returning(response.fold[Future[HttpResponse]](Future.failed(new Exception("")))(r ⇒ Future.successful(r)))

  def mockAuditBarsEvent(expectedEvent: BARSCheck, nino: NINO)() =
    (mockAuditor.sendEvent(_: HTSEvent, _: NINO)(_: ExecutionContext))
      .expects(expectedEvent, nino, *)
      .returning(())

  val service = new BarsServiceImpl(mockBarsConnector, mockMetrics, mockPagerDuty, mockAuditor)

  "The BarsService" when {

    "validating bank details" must {

      val nino = "NINO"
      val barsRequest = BankDetailsValidationRequest(nino, "123456", "accountNumber")
      implicit val request: Request[JsValue] = FakeRequest("GET", "/validate-bank-details").withBody(Json.toJson(barsRequest))
      val path = request.uri

        def newResponse(accountNumberWithSortCodeIsValid: Boolean, sortCodeIsPresentOnEISCD: String): String =
          s"""{
           |  "accountNumberWithSortCodeIsValid": $accountNumberWithSortCodeIsValid,
           |  "nonStandardAccountDetailsRequiredForBacs": "no",
           |  "sortCodeIsPresentOnEISCD":"$sortCodeIsPresentOnEISCD",
           |  "supportsBACS":"yes",
           |  "ddiVoucherFlag":"no",
           |  "directDebitsDisallowed": "yes",
           |  "directDebitInstructionsDisallowed": "yes"
           |}""".stripMargin

      "handle the case when the bank details are valid and the sort code exists" in {
        val response = newResponse(true, "yes")

        inSequence {
          mockBarsConnector(barsRequest)(Some(HttpResponse(200, Some(Json.parse(response)))))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        }
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(true, true))
      }

      "handle the case when the bank details are not valid" in {
        val response = newResponse(false, "no")

        inSequence {
          mockBarsConnector(barsRequest)(Some(HttpResponse(200, Some(Json.parse(response)))))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        }
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(false, false))
      }

      "handle the case when the bank details are valid but the sort code does not exist" in {
        val response = newResponse(true, "no")

        inSequence {
          mockBarsConnector(barsRequest)(Some(HttpResponse(200, Some(Json.parse(response)))))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        }
        val result = await(service.validate(barsRequest))
        result shouldBe Right(BankDetailsValidationResult(true, false))
      }

      "handle the case when the bank details are valid but the sort code response cannot be parsed" in {
        val response = newResponse(true, "blah")

        inSequence {
          mockBarsConnector(barsRequest)(Some(HttpResponse(200, Some(Json.parse(response)))))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)
        }
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

        inSequence {
          mockBarsConnector(barsRequest)(Some(HttpResponse(200, Some(Json.parse(response)))))
          mockAuditBarsEvent(BARSCheck(barsRequest, Json.parse(response), path), nino)

        }
        mockPagerDutyAlert("error parsing the response json from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("error parsing the response json from bars check")
      }

      "handle unsuccessful response from bars check" in {
        mockBarsConnector(barsRequest)(Some(HttpResponse(400)))
        mockPagerDutyAlert("unexpected status from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("unexpected status from bars check")
      }

      "recover from unexpected errors" in {
        mockBarsConnector(barsRequest)(None)
        mockPagerDutyAlert("unexpected error from bars check")
        val result = await(service.validate(barsRequest))
        result shouldBe Left("unexpected error from bars check")
      }

    }

  }
}
