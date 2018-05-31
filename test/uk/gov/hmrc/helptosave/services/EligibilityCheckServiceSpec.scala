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

import akka.actor.ActorRef
import akka.testkit.TestProbe
import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.handlers.CallHandler3
import org.scalatest.EitherValues
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosave.actors.ActorTestSupport
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, UCResponse}
import uk.gov.hmrc.helptosave.modules.ThresholdManagerProvider
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckServiceSpec extends ActorTestSupport("EligibilityCheckServiceSpec") with TestSupport with EitherValues {

  class TestThresholdProvider extends ThresholdManagerProvider {
    val probe = TestProbe()
    override val thresholdManager: ActorRef = probe.ref
  }

  private val mockEligibilityConnector = mock[EligibilityCheckConnector]
  private val mockProxyConnector = mock[HelpToSaveProxyConnector]
  private val mockAuditConnector = mock[AuditConnector]

  val htsAuditor = new HTSAuditor(mockAuditConnector)

  val thresholdManagerProvider = new TestThresholdProvider

  def newEligibilityCheckService(testConfig: Configuration): EligibilityCheckServiceImpl =
    new EligibilityCheckServiceImpl(mockProxyConnector, mockEligibilityConnector, htsAuditor, thresholdManagerProvider)(
      transformer,
      new AppConfig(fakeApplication.injector.instanceOf[Configuration] ++ testConfig, fakeApplication.injector.instanceOf[Environment])
    )

  def testConfiguration(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |uc-threshold {
       |  enabled = $enabled
       |  threshold-amount = 650.0
       |  ask-timeout = 10 seconds
       |}
    """.stripMargin
  ))

  private def mockDESEligibilityCheck(nino: String, uCResponse: Option[UCResponse])(response: Either[String, EligibilityCheckResult]) = {
    (mockEligibilityConnector.isEligible(_: String, _: Option[UCResponse])(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, uCResponse, *, *)
      .returning {
        EitherT.fromEither(response)
      }
  }

  private def mockUCClaimantCheck(nino: String, threshold: Double)(response: Either[String, UCResponse]) = {
    (mockProxyConnector.ucClaimantCheck(_: String, _: UUID, _: Double)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (ninoP, _, threshold, _, _) ⇒ ninoP === nino })
      .returning {
        EitherT.fromEither(response)
      }
  }

  def mockAuditEligibilityEvent(): CallHandler3[DataEvent, HeaderCarrier, ExecutionContext, Future[AuditResult]] =
    (mockAuditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (dataEvent, _, _) ⇒ dataEvent.auditType === "EligibilityResult" })
      .returning(Future.successful(AuditResult.Success))

  "EligibilityCheckService" when {

    val nino = "AE123456C"
    val uCResponse = UCResponse(true, Some(true))
    val threshold = 650.0

    val eligibilityCheckResponse = EligibilityCheckResult("eligible", 1, "tax credits", 1)

    "handling eligibility calls" must {

      "handle happy path and return result as expected" in {
        val config = testConfiguration(true)
        val eligibilityCheckService = newEligibilityCheckService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Right(eligibilityCheckResponse))
          mockAuditEligibilityEvent()
        }

        val result = eligibilityCheckService.getEligibility(nino).value
        thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
        thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

        await(result) shouldBe Right(eligibilityCheckResponse)
      }

      "call DES even if there is an errors during UC claimant check" in {
        val config = testConfiguration(true)
        val eligibilityCheckService = newEligibilityCheckService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Left("unexpected error during UCClaimant check"))
          mockDESEligibilityCheck(nino, None)(Right(eligibilityCheckResponse))
          mockAuditEligibilityEvent()
        }

        val result = eligibilityCheckService.getEligibility(nino).value
        thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
        thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

        await(result) shouldBe Right(eligibilityCheckResponse)
      }

      "map DES responses with result code 4 to an error" in {
        val config = testConfiguration(true)
        val eligibilityCheckService = newEligibilityCheckService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Right(eligibilityCheckResponse.copy(resultCode = 4)))
        }

        val result = eligibilityCheckService.getEligibility(nino).value
        thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
        thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

        await(result).isLeft shouldBe true
      }

      "handle errors during DES eligibility check check" in {
        val config = testConfiguration(true)
        val eligibilityCheckService = newEligibilityCheckService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Left("unexpected error during DES eligibility check"))
        }

        val result = eligibilityCheckService.getEligibility(nino).value
        thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
        thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

        await(result) shouldBe Left("unexpected error during DES eligibility check")

      }

      "stop and return an error when obtaining the threshold is enabled but the threshold manager returns None" in {
        val config = testConfiguration(true)
        val eligibilityCheckService = newEligibilityCheckService(config)

        val result = eligibilityCheckService.getEligibility(nino).value
        thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
        thresholdManagerProvider.probe.reply(GetThresholdValueResponse(None))

        await(result) shouldBe Left("Could not get threshold value")
      }

      "return the result as expected when obtaining the threshold is disabled" in {
        val config = testConfiguration(false)
        val eligibilityCheckService = newEligibilityCheckService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(Right(eligibilityCheckResponse))
          mockAuditEligibilityEvent()
        }

        val result = eligibilityCheckService.getEligibility(nino).value

        await(result) shouldBe Right(eligibilityCheckResponse)
      }

    }
  }

}
