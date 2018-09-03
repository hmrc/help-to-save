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
import cats.instances.int._
import cats.syntax.eq._
import com.typesafe.config.ConfigFactory
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.EitherValues
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosave.actors.ActorTestSupport
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.{DESConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.controllers.routes
import uk.gov.hmrc.helptosave.models.AccountCreated.{AllDetails, ExistingDetails, ManuallyEnteredDetails}
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.modules.ThresholdManagerProvider
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestEnrolmentBehaviour}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}

class HelpToSaveServiceSpec extends ActorTestSupport("HelpToSaveServiceSpec") with TestEnrolmentBehaviour with EitherValues with MockPagerDuty
  with GeneratorDrivenPropertyChecks with TestData {

  class TestThresholdProvider extends ThresholdManagerProvider {
    val probe = TestProbe()
    override val thresholdManager: ActorRef = probe.ref
  }

  private val mockDESConnector = mock[DESConnector]
  private val mockProxyConnector = mock[HelpToSaveProxyConnector]
  val mockAuditor = mock[HTSAuditor]

  val threshold = 650.0

  val thresholdManagerProvider = new TestThresholdProvider

  def newHelpToSaveService(testConfig: Configuration): HelpToSaveServiceImpl =
    new HelpToSaveServiceImpl(mockProxyConnector, mockDESConnector, mockAuditor, mockMetrics, mockPagerDuty, thresholdManagerProvider)(
      transformer,
      new AppConfig(fakeApplication.injector.instanceOf[Configuration] ++ testConfig, fakeApplication.injector.instanceOf[Environment])
    )

  def testConfiguration(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |uc-threshold {
       |  enabled = $enabled
       |  threshold-amount = $threshold
       |  ask-timeout = 10 seconds
       |}
    """.stripMargin
  ))

  private def mockDESEligibilityCheck(nino: String, uCResponse: Option[UCResponse])(response: HttpResponse) = {
    (mockDESConnector.isEligible(_: String, _: Option[UCResponse])(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, uCResponse, *, *)
      .returning(toFuture(response))
  }

  private def mockUCClaimantCheck(nino: String, threshold: Double)(result: Either[String, UCResponse]) = {
    (mockProxyConnector.ucClaimantCheck(_: String, _: UUID, _: Double)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, threshold, *, *)
      .returning(EitherT.fromEither[Future](result))
  }

  def mockSendAuditEvent(event: HTSEvent, nino: String) =
    (mockAuditor.sendEvent(_: HTSEvent, _: String)(_: ExecutionContext))
      .expects(event, nino, *)
      .returning(())

  def mockSetFlag(nino: String)(response: HttpResponse) =
    (mockDESConnector.setFlag(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(toFuture(response))

  def mockPayeGet(nino: String)(response: Option[HttpResponse]) =
    (mockDESConnector.getPersonalDetails(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  implicit val resultArb: Arbitrary[EligibilityCheckResult] = Arbitrary(for {
    result ← Gen.alphaStr
    resultCode ← Gen.choose(1, 10)
    reason ← Gen.alphaStr
    reasonCode ← Gen.choose(1, 10)
  } yield EligibilityCheckResult(result, resultCode, reason, reasonCode))

  "HelpToSaveService" when {

    val config = testConfiguration(false)
    val service = newHelpToSaveService(config)

    val nino = "AE123456C"
    val uCResponse = UCResponse(true, Some(true))
    val uCUnknownThresholdResponse = UCResponse(true, None)

    val eligibilityCheckResponse = EligibilityCheckResult("eligible", 1, "tax credits", 1)

    val jsonCheckResponse =
      """{
         |"result" : "eligible",
         |"resultCode" : 1,
         |"reason" : "tax credits",
         |"reasonCode" : 1
         |}
       """.stripMargin

    val jsonCheckResponseReasonCode4 =
      """{
         |"result" : "eligible",
         |"resultCode" : 4,
         |"reason" : "tax credits",
         |"reasonCode" : 4
         |}
       """.stripMargin

    "handling eligibility calls" must {
      val nino = randomNINO()

      "return with the eligibility check result unchanged from ITMP" in {
        val uCResponse = UCResponse(false, Some(false))
        forAll { result: EligibilityCheckResult ⇒
          whenever(result.resultCode =!= 4) {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(result)))) // scalastyle:ignore magic.number
            mockSendAuditEvent(EligibilityCheckEvent(nino, result, Some(uCResponse), "path"), nino)
            Await.result(service.getEligibility(nino, "path").value, 5.seconds) shouldBe Right(result)
          }
        }
      }

      "pass the UC params to DES if they are provided" in {
        val uCResponse = UCResponse(true, Some(true))
        forAll { result: EligibilityCheckResult ⇒
          whenever(result.resultCode =!= 4) {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(result)))) // scalastyle:ignore magic.number
            mockSendAuditEvent(EligibilityCheckEvent(nino, result, Some(uCResponse), "path"), nino)
            Await.result(service.getEligibility(nino, "path").value, 5.seconds) shouldBe Right(result)
          }
        }
      }

      "do not pass the UC withinThreshold param to DES if its not set" in {
        val uCResponse = UCResponse(true, None)
        forAll { result: EligibilityCheckResult ⇒
          whenever(result.resultCode =!= 4) {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(result)))) // scalastyle:ignore magic.number
            mockSendAuditEvent(EligibilityCheckEvent(nino, result, Some(uCResponse), "path"), nino)
            Await.result(service.getEligibility(nino, "path").value, 5.seconds) shouldBe Right(result)
          }
        }
      }

      "handle errors when parsing invalid json" in {
        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}""")))) // scalastyle:ignore magic.number
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }

        Await.result(service.getEligibility(nino, "path").value, 5.seconds) shouldBe
          Left("Could not parse http response JSON: /reasonCode: [error.path.missing]; /result: " +
            "[error.path.missing]; /resultCode: [error.path.missing]; /reason: [error.path.missing]. Response body was " +
            "\"{\\\"invalid\\\": \\\"foo\\\"}\"")
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(500, None))
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to check eligibility")
          }

          Await.result(service.getEligibility(nino, "path").value, 5.seconds).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
                mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(status, None))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Received unexpected http status in response to eligibility check")
              }

              Await.result(service.getEligibility(nino, "path").value, 5.seconds).isLeft shouldBe true
            }

          }

        }

      }

      "handle when uc threshold is enabled" when {

        "on the happy path and return result as expected" in {
          val config = testConfiguration(true)
          val eligibilityCheckService = newHelpToSaveService(config)

          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
            mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)
          }

          val result = eligibilityCheckService.getEligibility(nino, "path").value
          thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
          thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

          await(result) shouldBe Right(eligibilityCheckResponse)
        }

        "call DES even if there is an errors during UC claimant check" in {
          val config = testConfiguration(true)
          val eligibilityCheckService = newHelpToSaveService(config)

          inSequence {
            mockUCClaimantCheck(nino, threshold)(Left("unexpected error during UCClaimant check"))
            mockDESEligibilityCheck(nino, None)(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
            mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, None, "path"), nino)
          }

          val result = eligibilityCheckService.getEligibility(nino, "path").value
          thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
          thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

          await(result) shouldBe Right(eligibilityCheckResponse)
        }

        "map DES responses with result code 4 to an error" in {
          val config = testConfiguration(true)
          val eligibilityCheckService = newHelpToSaveService(config)

          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.parse(jsonCheckResponseReasonCode4))))
            mockPagerDutyAlert("Received result code 4 from DES eligibility check")
          }

          val result = eligibilityCheckService.getEligibility(nino, "path").value
          thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
          thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

          await(result).isLeft shouldBe true
        }

        "handle errors during DES eligibility check" in {
          val config = testConfiguration(true)
          val eligibilityCheckService = newHelpToSaveService(config)

          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(500, Some(Json.toJson("unexpected error during DES eligibility check"))))
            mockPagerDutyAlert("Received unexpected http status in response to eligibility check")
          }

          val result = eligibilityCheckService.getEligibility(nino, "path").value
          thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
          thresholdManagerProvider.probe.reply(GetThresholdValueResponse(Some(threshold)))

          await(result) shouldBe Left("Received unexpected status 500")

        }

        "continue the eligibility check when obtaining the threshold is enabled but the threshold manager returns None and the applicant" +
          "is eligible from a WTC perspective" in {
            val config = testConfiguration(true)
            val eligibilityCheckService = newHelpToSaveService(config)

            inSequence {
              mockDESEligibilityCheck(nino, None)(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
              mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, None, "path"), nino)
            }

            val result = eligibilityCheckService.getEligibility(nino, "path").value
            thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
            thresholdManagerProvider.probe.reply(GetThresholdValueResponse(None))

            await(result) shouldBe Right(eligibilityCheckResponse)
          }

        "continue the eligibility check when obtaining the threshold is enabled but the threshold manager returns None and the applicant" +
          "is not eligible from a WTC perspective so the user's journey ends in a technical error" in {
            val config = testConfiguration(true)
            val eligibilityCheckService = newHelpToSaveService(config)

            inSequence {
              mockDESEligibilityCheck(nino, None)(HttpResponse(500, Some(Json.toJson("eligibility check was inconclusive"))))
              mockPagerDutyAlert("Received unexpected http status in response to eligibility check")
            }

            val result = eligibilityCheckService.getEligibility(nino, "path").value
            thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
            thresholdManagerProvider.probe.reply(GetThresholdValueResponse(None))

            await(result) shouldBe Left("Received unexpected status 500")
          }

      }

      "return the result as expected when obtaining the threshold is disabled" in {
        val config = testConfiguration(false)
        val eligibilityCheckService = newHelpToSaveService(config)

        inSequence {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
          mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)
        }

        val result = eligibilityCheckService.getEligibility(nino, "path").value

        await(result) shouldBe Right(eligibilityCheckResponse)
      }

    }

    "handling setFlag calls" must {

      "return a Right when call to ITMP comes back with 200" in {
        mockSetFlag(nino)(HttpResponse(200))

        await(service.setFlag(nino).value).isRight shouldBe true

      }

      "return a Left" when {

        val nino = "NINO"

        "the call to ITMP comes back with a status which isn't 200 or 403" in {
          forAll{ status: Int ⇒
            whenever(status != 200 && status != 403){
              inSequence{
                mockSetFlag(nino)(HttpResponse(status))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Received unexpected http status in response to setting ITMP flag")
              }

              await(service.setFlag(nino).value).isLeft shouldBe true
            }
          }
        }

        "an error occurs while calling the ITMP endpoint" in {
          inSequence {
            mockSetFlag(nino)(HttpResponse(500))
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to set ITMP flag")
          }
          await(service.setFlag(nino).value).isLeft shouldBe true
        }

      }

    }

    "handling getPersonalDetails calls" must {

      val nino = "AA123456A"

      "return a Right when nino is successfully found in DES" in {
        mockPayeGet(nino)(Some(HttpResponse(200, Some(Json.parse(payeDetails(nino))))))
        Await.result(service.getPersonalDetails(nino).value, 5.seconds).isRight shouldBe true
      }

      "handle 404 response when a nino is not found in DES" in {
        mockPayeGet(nino)(Some(HttpResponse(404, None))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Received unexpected http status in response to paye-personal-details")
        Await.result(service.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
      }

      "handle errors when parsing invalid json" in {
        inSequence {
          mockPayeGet(nino)(Some(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        }
        Await.result(service.getPersonalDetails(nino).value, 15.seconds).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockPayeGet(nino)(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to paye-personal-details")
          }

          Await.result(service.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockPayeGet(nino)(Some(HttpResponse(status)))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Received unexpected http status in response to paye-personal-details")
              }

              Await.result(service.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
            }

          }

        }
      }

    }

  }

}
