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

package uk.gov.hmrc.helptosave.controllers

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.Status.{BAD_REQUEST, CREATED, CONFLICT, OK}
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, NSIUserInfo}
import uk.gov.hmrc.helptosave.services.{EligibilityCheckService, UserCapService}
import uk.gov.hmrc.helptosave.util.{NINO, toFuture}
import uk.gov.hmrc.helptosave.utils.{TestEnrolmentBehaviour, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveControllerSpec extends TestSupport with TestEnrolmentBehaviour {

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  class TestApparatus {
    val proxyConnector = mock[HelpToSaveProxyConnector]

    val userCapService = mock[UserCapService]

    val eligibilityCheckService = mock[EligibilityCheckService]

    val controller = new HelpToSaveController(enrolmentStore, itmpConnector, proxyConnector, userCapService, eligibilityCheckService)

    def mockCreateAccount(expectedPayload: NSIUserInfo)(response: HttpResponse) =
      (proxyConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
        .expects(expectedPayload, *, *)
        .returning(toFuture(response))

    def mockUpdateEmail(expectedPayload: NSIUserInfo)(response: HttpResponse) =
      (proxyConnector.updateEmail(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
        .expects(expectedPayload, *, *)
        .returning(toFuture(response))

    def mockUserCapServiceUpdate(result: Either[String, Unit]) = {
      (userCapService.update()(_: ExecutionContext))
        .expects(*)
        .returning(result.fold[Future[Unit]](e ⇒ Future.failed(new Exception(e)), _ ⇒ Future.successful(())))
    }

    def mockEligibilityCheckerService(nino: NINO)(result: Either[String, EligibilityCheckResult]): Unit =
      (eligibilityCheckService.getEligibility(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))
  }

  "The HelpToSaveNonDigitalController" when {

      def jsonString(dobValue: String): String =
        s"""{
         | "nino" : "nino",
         | "forename" : "name",
         | "surname" : "surname",
         | "dateOfBirth" : $dobValue,
         | "contactDetails" : {
         |     "address1" : "1",
         |     "address2" : "2",
         |     "postcode": "postcode",
         |     "countryCode" : "country",
         |     "communicationPreference" : "preference"
         | },
         | "registrationChannel" : "channel"
         |}
           """.stripMargin

    val validJSONPayload = Json.parse(jsonString("20200101"))
    val validNSIUserInfo = validJSONPayload.validate[NSIUserInfo].getOrElse(sys.error("Could not parse NSIUserInfo"))

    "create account" must {

      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        inSequence {
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreUpdate("nino", false)(Right(()))
          inAnyOrder {
            mockITMPConnector("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
            mockUserCapServiceUpdate(Right(()))
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validJSONPayload))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account if the request is valid NSIUserInfo json even if updating the enrolment store fails" in new TestApparatus {
        inSequence {
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          inAnyOrder {
            mockEnrolmentStoreUpdate("nino", false)(Left(""))
            mockUserCapServiceUpdate(Right(()))
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validJSONPayload))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account if the request is valid NSIUserInfo json even if updating the user counts fails" in new TestApparatus {
        inSequence {
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreUpdate("nino", false)(Right(()))
          inAnyOrder {
            mockITMPConnector("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
            mockUserCapServiceUpdate(Left(""))
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validJSONPayload))

        status(result)(10.seconds) shouldBe CREATED
      }

      "return bad request response if the request body is not a valid NSIUserInfo json" in new TestApparatus {

        val requestBody = Json.parse(jsonString("\"123456\""))
        val result = controller.createAccount()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        val result = controller.createAccount()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetail":""}"""
      }

      "handle 409 response from proxy" in new TestApparatus {
        inSequence {
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CONFLICT))
          mockEnrolmentStoreUpdate("nino", false)(Right(()))
          inAnyOrder {
            mockITMPConnector("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validJSONPayload))

        status(result)(10.seconds) shouldBe CONFLICT
      }

    }

    "update email" must {
      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        mockUpdateEmail(validNSIUserInfo)(HttpResponse(OK))

        val result = controller.updateEmail()(FakeRequest().withJsonBody(validJSONPayload))

        status(result)(10.seconds) shouldBe OK
      }

      "return bad request response if the request body is not a valid NSIUserInfo json" in new TestApparatus {

        val requestBody = Json.parse(jsonString("\"123456\""))
        val result = controller.updateEmail()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        val result = controller.updateEmail()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetail":""}"""
      }
    }

    "checking eligibility for an API User" must {

      val nino = "AE123456"

        def doRequest(controller: HelpToSaveController) =
          controller.checkEligibility(nino)(FakeRequest())

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        mockEligibilityCheckerService(nino)(Left("The Eligibility Check service is unavailable"))

        val result = doRequest(controller)
        status(result) shouldBe 500
      }

      "return the eligibility status returned from the eligibility check service if " +
        "successful" in new TestApparatus {
          val eligibility = EligibilityCheckResult("x", 0, "y", 0)
          mockEligibilityCheckerService(nino)(Right(eligibility))

          val result = doRequest(controller)
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(eligibility)
        }
    }
  }

}
