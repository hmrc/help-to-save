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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.testkit.TestProbe
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{doNothing, when}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosave.actors.ActorTestSupport
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.{DESConnector, HelpToSaveProxyConnector, IFConnector}
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.modules.{MDTPThresholdOrchestrator, ThresholdValueByConfigProvider, UCThresholdOrchestrator}
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Provider
import scala.concurrent.Future

class HelpToSaveServiceSpec
    extends ActorTestSupport("HelpToSaveServiceSpec")
    with MockPagerDuty
    with TestData
    with ScalaCheckDrivenPropertyChecks {

  class TestUCThresholdOrchestrator
      extends UCThresholdOrchestrator(
        system,
        mockPagerDuty,
        fakeApplication.injector.instanceOf[Configuration],
        mockDESConnector
      ) {
    val probe: TestProbe                    = TestProbe()
    override val thresholdManager: ActorRef = probe.ref
  }

  private val mockDESConnector   = mock[DESConnector]
  private val mockIFConnector    = mock[IFConnector]
  private val mockProxyConnector = mock[HelpToSaveProxyConnector]
  private val mockAuditor        = mock[HTSAuditor]
  private val returnHeaders      = Map[String, Seq[String]]()

  private val mdtpMockThresholdOrchestrator      = mock[MDTPThresholdOrchestrator]
  private val mdtpthresholdValueByConfigProvider = mock[Provider[MDTPThresholdOrchestrator]]

  private val ucMockThresholdOrchestrator      = mock[UCThresholdOrchestrator]
  private val ucThresholdValueByConfigProvider = mock[Provider[UCThresholdOrchestrator]]

  private val threshold = 1.23

  private val testUCThresholdOrchestrator    = new TestUCThresholdOrchestrator
  private val thresholdValueByConfigProvider =
    new ThresholdValueByConfigProvider(appConfig, ucThresholdValueByConfigProvider, mdtpthresholdValueByConfigProvider)

  private val service =
    new HelpToSaveServiceImpl(
      mockProxyConnector,
      mockDESConnector,
      mockIFConnector,
      mockAuditor,
      mockMetrics,
      mockPagerDuty,
      thresholdValueByConfigProvider
    )(
      transformer,
      new AppConfig(
        fakeApplication.injector.instanceOf[Configuration],
        fakeApplication.injector.instanceOf[Environment],
        servicesConfig
      )
    )

  private def mockDESEligibilityCheck(nino: String, uCResponse: Option[UCResponse])(response: HttpResponse) =
    when(mockDESConnector.isEligible(eqTo(nino), eqTo(uCResponse))(any(), any())).thenReturn(toFuture(Right(response)))

  private def mockUCClaimantCheck(nino: String, threshold: Double)(result: Either[String, UCResponse]) =
    when(mockProxyConnector.ucClaimantCheck(eqTo(nino), any(), eqTo(threshold))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  private def mockSendAuditEvent(event: HTSEvent, nino: String): Unit =
    doNothing().when(mockAuditor).sendEvent(eqTo(event), eqTo(nino))(any())

  private def mockSetFlag(nino: String)(response: HttpResponse) =
    when(mockDESConnector.setFlag(eqTo(nino))(any(), any())).thenReturn(toFuture(Right(response)))

  private def mockPayeGet(nino: String)(response: HttpResponse) =
    when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any())).thenReturn(toFuture(Right(response)))

  private def mockIFPayeGet(nino: String)(response: HttpResponse) =
    when(mockIFConnector.getPersonalDetails(eqTo(nino))(any())).thenReturn(toFuture(Right(response)))

  implicit val resultArb: Arbitrary[EligibilityCheckResult] = Arbitrary(for {
    result     <- Gen.alphaStr
    resultCode <- Gen.choose(1, 10)
    reason     <- Gen.alphaStr
    reasonCode <- Gen.choose(1, 10)
  } yield EligibilityCheckResult(result, resultCode, reason, reasonCode))

  "HelpToSaveService" when {

    val nino       = "AE123456C"
    val uCResponse = UCResponse(ucClaimant = true, Some(true))

    val wtcEligibleResponse = EligibilityCheckResult("eligible", 1, "tax credits", 1)

    val jsonCheckResponse =
      """{
        |"result" : "eligible",
        |"resultCode" : 1,
        |"reason" : "tax credits",
        |"reasonCode" : 1
        |}
      """.stripMargin

    "handling eligibility calls" must {
      val nino = randomNINO()

      def getEligibility(thresholdResponse: Option[Double]): Either[NINO, EligibilityCheckResponse] = {
        when(ucThresholdValueByConfigProvider.get()).thenReturn(testUCThresholdOrchestrator)
        when(mdtpthresholdValueByConfigProvider.get()).thenReturn(mdtpMockThresholdOrchestrator)

        when(ucMockThresholdOrchestrator.getValue).thenReturn(Future.successful(Some(threshold)))
        when(mdtpMockThresholdOrchestrator.getValue).thenReturn(Future.successful(thresholdResponse))

        val result = service.getEligibility(nino, "path").value

        if (!appConfig.useMDTPThresholdConfig) {
          testUCThresholdOrchestrator.probe.expectMsg(GetThresholdValue)
          testUCThresholdOrchestrator.probe.reply(GetThresholdValueResponse(thresholdResponse))
        }

        await(result)
      }

      "return with the eligibility check result unchanged from ITMP" in {
        val uCResponse = UCResponse(ucClaimant = false, Some(false))
        forAll { (eligibilityCheckResponse: EligibilityCheckResult) =>
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(
            HttpResponse(200, Json.toJson(eligibilityCheckResponse), returnHeaders)
          ) // scalastyle:ignore magic.number
          mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)

          getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
        }
      }

      "call DES even if there is an errors during UC claimant check" in {
        mockUCClaimantCheck(nino, threshold)(Left("unexpected error during UCClaimant check"))
        mockDESEligibilityCheck(nino, None)(HttpResponse(200, Json.parse(jsonCheckResponse), returnHeaders))
        mockSendAuditEvent(EligibilityCheckEvent(nino, wtcEligibleResponse, None, "path"), nino)

        getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(wtcEligibleResponse, Some(1.23)))
      }

      "continue the eligibility check when the threshold cannot be retrieved and the applicant is eligible from a WTC perspective" in {
        mockDESEligibilityCheck(nino, None)(HttpResponse(200, Json.parse(jsonCheckResponse), returnHeaders))
        mockSendAuditEvent(EligibilityCheckEvent(nino, wtcEligibleResponse, None, "path"), nino)

        getEligibility(None) shouldBe Right(EligibilityCheckResponse(wtcEligibleResponse, None))
      }

      "pass the UC params to DES if they are provided" in {
        val uCResponse = UCResponse(ucClaimant = true, Some(true))
        forAll { (eligibilityCheckResponse: EligibilityCheckResult) =>
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(
            HttpResponse(200, Json.toJson(eligibilityCheckResponse), returnHeaders)
          ) // scalastyle:ignore magic.number
          mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)

          getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
        }
      }

      "do not pass the UC withinThreshold param to DES if its not set" in {
        val uCResponse = UCResponse(ucClaimant = true, None)
        forAll { (eligibilityCheckResponse: EligibilityCheckResult) =>
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(
            HttpResponse(200, Json.toJson(eligibilityCheckResponse), returnHeaders)
          ) // scalastyle:ignore magic.number
          mockSendAuditEvent(EligibilityCheckEvent(nino, eligibilityCheckResponse, Some(uCResponse), "path"), nino)

          getEligibility(Some(threshold)) shouldBe Right(EligibilityCheckResponse(eligibilityCheckResponse, Some(1.23)))
        }
      }

      "return with an error" when {
        "the call to DES fails" in {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(500, ""))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to check eligibility")

          getEligibility(Some(threshold)) shouldBe Left("Received unexpected status 500")
        }

        "the call comes back with an unexpected http status" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
              mockDESEligibilityCheck(nino, Some(uCResponse))(HttpResponse(status, ""))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to eligibility check")

              getEligibility(Some(threshold)) shouldBe Left(s"Received unexpected status $status")
            }
          }
        }

        "parsing invalid json" in {
          mockUCClaimantCheck(nino, threshold)(Right(uCResponse))
          mockDESEligibilityCheck(nino, Some(uCResponse))(
            HttpResponse(200, Json.toJson("""{"invalid": "foo"}"""), returnHeaders)
          ) // scalastyle:ignore magic.number
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")

          getEligibility(Some(threshold)) shouldBe
            Left(
              "Could not parse http response JSON: : [error.expected.jsobject]. Response body was " +
                "\"{\\\"invalid\\\": \\\"foo\\\"}\""
            )
        }
      }
    }

    "handling setFlag calls" must {
      "return a Right when call to ITMP comes back with 200" in {
        mockSetFlag(nino)(HttpResponse(200, ""))

        await(service.setFlag(nino).value) shouldBe Right(())
      }

      "return a Left" when {
        val nino = "NINO"

        "the call to ITMP comes back with a status which isn't 200 or 403" in {
          forAll { (status: Int) =>
            whenever(status != 200 && status != 403) {
              mockSetFlag(nino)(HttpResponse(status, ""))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to setting ITMP flag")

              await(service.setFlag(nino).value) shouldBe Left(
                s"Received unexpected response status ($status) when trying to set ITMP flag. Body was:  (round-trip time: 0ns)"
              )
            }
          }
        }

        "an error occurs while calling the ITMP endpoint" in {
          mockSetFlag(nino)(HttpResponse(500, ""))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to set ITMP flag")
          await(service.setFlag(nino).value) shouldBe Left(
            "Received unexpected response status (500) when trying to set ITMP flag. Body was:  (round-trip time: 0ns)"
          )
        }
      }
    }

    "handling getPersonalDetails DES calls" must {
      val serviceWithDES =
        new HelpToSaveServiceImpl(
          mockProxyConnector,
          mockDESConnector,
          mockIFConnector,
          mockAuditor,
          mockMetrics,
          mockPagerDuty,
          thresholdValueByConfigProvider
        )(
          transformer,
          new AppConfig(
            fakeApplication.injector.instanceOf[Configuration],
            fakeApplication.injector.instanceOf[Environment],
            new ServicesConfig(
              Configuration(
                ConfigFactory.parseString("""
                  | feature.if.enabled = false
            """.stripMargin)
              ).withFallback(configuration)
            )
          )
        )
      val nino           = "AA123456A"

      "return a Right when nino is successfully found in DES" in {
        when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any()))
          .thenReturn(toFuture(Right(HttpResponse(200, Json.parse(payeDetails(nino)), returnHeaders))))

        await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Right(ppDetails)
      }

      "handle 404 response when a nino is not found in DES" in {
        when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any()))
          .thenReturn(toFuture(Left(UpstreamErrorResponse("", 404))))

        mockPagerDutyAlert("[DES] Received unexpected http status in response to paye-personal-details")
        await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Left(
          "Call to paye-personal-details unsuccessful:  (round-trip time: (round-trip time: 0ns))"
        )
      }

      "handle errors when parsing invalid json" in {
        when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any()))
          .thenReturn(toFuture(Right(HttpResponse(200, Json.toJson("""{"invalid": "foo"}"""), returnHeaders))))

        // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
        mockPagerDutyAlert("[DES] Could not parse JSON in the paye-personal-details response")
        await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Left(
          "Could not parse http response JSON: : [No Name found in the DES response]"
        )
      }

      "handle errors when parsing json with personal details containing no Postcode " in {
        when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any()))
          .thenReturn(toFuture(Right(HttpResponse(200, Json.parse(payeDetailsNoPostCode(nino)), returnHeaders))))

        mockPagerDutyAlert("[DES] Could not parse JSON in the paye-personal-details response")
        await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Left(
          "Could not parse http response JSON: : ['postcode' is undefined on object: line1,line2,line3,line4,countryCode,line5,sequenceNumber,startDate]"
        )
      }

      "return with an error" when {
        "the call fails" in {
          when(mockDESConnector.getPersonalDetails(eqTo(nino))(any(), any()))
            .thenReturn(toFuture(Left(UpstreamErrorResponse("", 500))))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("[DES] Failed to make call to paye-personal-details")

          await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Left(
            "Call to paye-personal-details unsuccessful:  (round-trip time: (round-trip time: 0ns))"
          )
        }

        "the call comes back with an unexpected http status" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              mockPayeGet(nino)(HttpResponse(status, ""))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("[DES] Received unexpected http status in response to paye-personal-details")

              await(serviceWithDES.getPersonalDetails(nino).value) shouldBe Left(s"Received unexpected status $status")
            }
          }
        }
      }
    }

    "handling getPersonalDetails IF calls" must {
      val serviceWithIf =
        new HelpToSaveServiceImpl(
          mockProxyConnector,
          mockDESConnector,
          mockIFConnector,
          mockAuditor,
          mockMetrics,
          mockPagerDuty,
          thresholdValueByConfigProvider
        )(
          transformer,
          new AppConfig(
            fakeApplication.injector.instanceOf[Configuration],
            fakeApplication.injector.instanceOf[Environment],
            new ServicesConfig(
              Configuration(
                ConfigFactory.parseString("""
                                          | feature.if.enabled = true
            """.stripMargin)
              ).withFallback(configuration)
            )
          )
        )

      val nino = "AA123456A"
      "return a Right when nino is successfully found in IF" in {
        when(mockIFConnector.getPersonalDetails(eqTo(nino))(any()))
          .thenReturn(toFuture(Right(HttpResponse(200, Json.parse(payeDetails(nino)), returnHeaders))))

        await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Right(ppDetails)
      }

      "handle 404 response when a nino is not found in IF" in {
        mockIFPayeGet(nino)(HttpResponse(404, "")) // scalastyle:ignore magic.number
        mockPagerDutyAlert("[IF] Received unexpected http status in response to paye-personal-details")
        await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Left("Received unexpected status 404")
      }

      "handle errors when parsing invalid json" in {
        mockIFPayeGet(nino)(
          HttpResponse(200, Json.toJson("""{"invalid": "foo"}"""), returnHeaders)
        ) // scalastyle:ignore magic.number
        // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
        mockPagerDutyAlert("[IF] Could not parse JSON in the paye-personal-details response")
        await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Left(
          "Could not parse http response JSON: : [No Name found in the DES response]"
        )
      }

      "handle errors when parsing json with personal details containing no Postcode " in {
        mockIFPayeGet(nino)(HttpResponse(200, Json.parse(payeDetailsNoPostCode(nino)), returnHeaders))
        mockPagerDutyAlert("[IF] Could not parse JSON in the paye-personal-details response")
        await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Left(
          "Could not parse http response JSON: : ['postcode' is undefined on object: line1,line2,line3,line4,countryCode,line5,sequenceNumber,startDate]"
        )
      }

      "return with an error" when {
        "the call fails" in {
          when(mockIFConnector.getPersonalDetails(eqTo(nino))(any()))
            .thenReturn(toFuture(Left(UpstreamErrorResponse("", 500))))

          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("[IF] Failed to make call to paye-personal-details")

          await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Left(
            "Call to paye-personal-details unsuccessful:  (round-trip time: (round-trip time: 0ns))"
          )
        }

        "the call comes back with an unexpected http status" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              mockIFPayeGet(nino)(HttpResponse(status, ""))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("[IF] Received unexpected http status in response to paye-personal-details")

              await(serviceWithIf.getPersonalDetails(nino).value) shouldBe Left(s"Received unexpected status $status")
            }
          }
        }
      }
    }
  }
}
