/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.EitherValues
import play.api.Configuration
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, UCResponse}
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class EligibilityCheckServiceSpec extends TestSupport with EitherValues {

  private val mockEligibilityConnector = mock[EligibilityCheckConnector]
  private val mockProxyConnector = mock[HelpToSaveProxyConnector]
  private val mockAuditConnector = mock[AuditConnector]

  val htsAuditor = new HTSAuditor {
    override val auditConnector: AuditConnector = mockAuditConnector
  }

  private def mockDESEligibilityCheck(nino: String, uCResponse: Option[UCResponse])(response: Either[String, Option[EligibilityCheckResult]]) = {
    (mockEligibilityConnector.isEligible(_: String, _: Option[UCResponse])(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, uCResponse, *, *)
      .returning {
        EitherT.fromEither(response)
      }
  }

  private def mockUCClaimantCheck(nino: String)(response: Either[String, UCResponse]) = {
    (mockProxyConnector.ucClaimantCheck(_: String, _: UUID)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (ninoP, _, _, _) ⇒ ninoP === nino })
      .returning {
        EitherT.fromEither(response)
      }
  }

  def mockAuditEligibilityEvent() =
    (mockAuditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (dataEvent, _, _) ⇒ dataEvent.auditType === "EligibilityResult" })
      .returning(Future.successful(AuditResult.Success))

  "EligibilityCheckService" when {

    val nino = "AE123456C"
    val uCResponse = UCResponse(true, Some(true))

    val eligibilityCheckResponse = EligibilityCheckResult("eligible", 1, "tax credits", 1)

    "handling eligibility calls when UC is disabled" must {

      lazy val eligibilityCheckService = new EligibilityCheckServiceImpl(mockProxyConnector, mockEligibilityConnector, configuration, htsAuditor)

      "handle happy path and return result as expected" in {

        inSequence {
          mockDESEligibilityCheck(nino, None)(Right(Some(eligibilityCheckResponse)))
          mockAuditEligibilityEvent()
        }

        val result = await(eligibilityCheckService.getEligibility(nino).value)

        result shouldBe Right(Some(eligibilityCheckResponse))

      }

      "handle errors during DES eligibility check check" in {

        mockDESEligibilityCheck(nino, None)(Left("unexpected error during DES eligibility check"))

        val result = await(eligibilityCheckService.getEligibility(nino).value)

        result shouldBe Left("unexpected error during DES eligibility check")

      }
    }

    "handling eligibility calls when UC is enabled" must {

      "handle happy path and return result as expected" in {

        inSequence {
          mockUCClaimantCheck(nino)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Right(Some(eligibilityCheckResponse)))
          mockAuditEligibilityEvent()
        }

        val ucEnabledConfig = fakeApplication.configuration.++(Configuration("microservice.uc-enabled" -> true))
        val eligibilityCheckService = new EligibilityCheckServiceImpl(mockProxyConnector, mockEligibilityConnector, ucEnabledConfig, htsAuditor)

        val result = await(eligibilityCheckService.getEligibility(nino).value)

        result shouldBe Right(Some(eligibilityCheckResponse))

      }

      "call DES even if there is an errors during UC claimant check" in {
        inSequence {
          mockUCClaimantCheck(nino)(Left("unexpected error during UCClaimant check"))
          mockDESEligibilityCheck(nino, None)(Right(Some(eligibilityCheckResponse)))
          mockAuditEligibilityEvent()
        }

        val ucEnabledConfig = fakeApplication.configuration.++(Configuration("microservice.uc-enabled" -> "true"))
        val eligibilityCheckService = new EligibilityCheckServiceImpl(mockProxyConnector, mockEligibilityConnector, ucEnabledConfig, htsAuditor)

        val result = await(eligibilityCheckService.getEligibility(nino).value)

        result shouldBe Right(Some(eligibilityCheckResponse))

      }

      "handle errors during DES eligibility check check" in {

        inSequence {
          mockUCClaimantCheck(nino)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Left("unexpected error during DES eligibility check"))
        }

        val ucEnabledConfig = fakeApplication.configuration.++(Configuration("microservice.uc-enabled" -> "true"))
        val eligibilityCheckService = new EligibilityCheckServiceImpl(mockProxyConnector, mockEligibilityConnector, ucEnabledConfig, htsAuditor)

        val result = await(eligibilityCheckService.getEligibility(nino).value)

        result shouldBe Left("unexpected error during DES eligibility check")

      }
    }
  }

}
