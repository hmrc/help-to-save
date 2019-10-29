/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.actor.{ActorRef, ActorSystem}
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
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.modules.{ThresholdManagerProvider, UCThresholdOrchestrator}
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestEnrolmentBehaviour}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
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

  val threshold = 1.23

  val thresholdManagerProvider = new TestThresholdProvider

  val UCThresholdOrchestrator = new UCThresholdOrchestrator(fakeApplication.injector.instanceOf[ActorSystem],
                                                            mockPagerDuty,
                                                            fakeApplication.injector.instanceOf[Configuration],
                                                            mockDESConnector
  )

  val service: HelpToSaveServiceImpl = {
    val testConfig = Configuration(ConfigFactory.parseString("""uc-threshold { ask-timeout = 10 seconds }"""))

    new HelpToSaveServiceImpl(mockProxyConnector, mockDESConnector, mockAuditor, mockMetrics, mockPagerDuty, thresholdManagerProvider)(

      transformer,
      new AppConfig(fakeApplication.injector.instanceOf[Configuration] ++ testConfig,
        fakeApplication.injector.instanceOf[Environment],
        servicesConfig)
    )
  }

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

    val nino = "AE123456C"
    val uCResponse = UCResponse(true, Some(true))

    val wtcEligibleResponse = EligibilityCheckResult("eligible", 1, "tax credits", 1)

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

        def getEligibility(thresholdResponse: Option[Double]): Either[String, EligibilityCheckResponse] = {
          val result = service.getEligibility(nino, "path").value
          thresholdManagerProvider.probe.expectMsg(GetThresholdValue)
          thresholdManagerProvider.probe.reply(GetThresholdValueResponse(thresholdResponse))
          await(result)
        }

      "return with the eligibility check result unchanged from ITMP" in {
        val uCResponse = UCResponse(false, Some(false))
        forAll { eligibilityCheckResponse: EligibilityCheckResult ⇒
          whenever(eligibilityCheckResponse.resultCode =!= 4) {
            inSequence {
              mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
              mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse)))) // scalastyle:ignore magic.number
              mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)
            }

            getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
          }
        }
      }

      "call DES even if there is an errors during UC claimant check" in {
        inSequence {
          mockUCClaimantCheck(nino, threshold)(Left("unexpected error during UCClaimant check"))
          mockDESEligibilityCheck(nino, None)(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
          mockSendAuditEvent(EligibilityCheckEvent(nino, wtcEligibleResponse, None, "path"), nino)
        }

        getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(wtcEligibleResponse, Some(1.23)))
      }

      "continue the eligibility check when the threshold cannot be retrieved and the applicant is eligible from a WTC perspective" in {
        inSequence {
          mockDESEligibilityCheck(nino, None)(HttpResponse(200, Some(Json.parse(jsonCheckResponse))))
          mockSendAuditEvent(EligibilityCheckEvent(nino, wtcEligibleResponse, None, "path"), nino)
        }

        getEligibility(None) shouldBe Right(EligibilityCheckResponse(wtcEligibleResponse, None))
      }

      "pass the UC params to DES if they are provided" in {
        val uCResponse = UCResponse(true, Some(true))
        forAll { eligibilityCheckResponse: EligibilityCheckResult ⇒
          whenever(eligibilityCheckResponse.resultCode =!= 4) {
            inSequence {
              mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
              mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse)))) // scalastyle:ignore magic.number
              mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)
            }

            getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
          }
        }
      }

      "do not pass the UC withinThreshold param to DES if its not set" in {
        val uCResponse = UCResponse(true, None)
        forAll { eligibilityCheckResponse: EligibilityCheckResult ⇒
          whenever(eligibilityCheckResponse.resultCode =!= 4) {
            inSequence {
              mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
              mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse)))) // scalastyle:ignore magic.number
              mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)
            }

            getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
          }
        }
      }

      "return with an error" when {
        "the call to DES fails" in {
          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(500, None))
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to check eligibility")
          }

          getEligibility(Some(threshold)).isLeft shouldBe true
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

              getEligibility(Some(threshold)).isLeft shouldBe true
            }
          }
        }

        "parsing invalid json" in {
          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}""")))) // scalastyle:ignore magic.number
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Could not parse JSON in eligibility check response")
          }

          getEligibility(Some(threshold)) shouldBe
            Left("Could not parse http response JSON: : [error.expected.jsobject]. Response body was " +
              "\"{\\\"invalid\\\": \\\"foo\\\"}\"")
        }

        "DES returns result code 4" in {
          inSequence {
            mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
            mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(200, Some(Json.parse(jsonCheckResponseReasonCode4))))
            mockPagerDutyAlert("Received result code 4 from DES eligibility check")
          }

          getEligibility(Some(threshold)).isLeft shouldBe true
        }

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
