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
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.Status._
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.GGAndPrivilegedProviders
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.services.{BarsService, UserCapService}
import uk.gov.hmrc.helptosave.util.{NINO, toFuture}
import uk.gov.hmrc.helptosave.utils.TestEnrolmentBehaviour
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveControllerSpec extends AuthSupport with TestEnrolmentBehaviour {

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  class TestApparatus {

    val proxyConnector = mock[HelpToSaveProxyConnector]

    val userCapService = mock[UserCapService]

    val mockAuditor = mock[HTSAuditor]

    val emailStore = mock[EmailStore]

    val barsService = mock[BarsService]

    val controller = new HelpToSaveController(
      enrolmentStore,
      emailStore,
      proxyConnector,
      userCapService,
      helpToSaveService,
      mockAuthConnector,
      mockAuditor,
      barsService)

    def mockSendAuditEvent(event: HTSEvent, nino: String) =
      (mockAuditor.sendEvent(_: HTSEvent, _: String)(_: ExecutionContext))
        .expects(event, nino, *)
        .returning(())

    def mockCreateAccount(expectedPayload: NSIPayload)(response: HttpResponse) =
      (proxyConnector.createAccount(_: NSIPayload)(_: HeaderCarrier, _: ExecutionContext))
        .expects(expectedPayload, *, *)
        .returning(toFuture(response))

    def mockUpdateEmail(expectedPayload: NSIPayload)(response: HttpResponse) =
      (proxyConnector.updateEmail(_: NSIPayload)(_: HeaderCarrier, _: ExecutionContext))
        .expects(expectedPayload, *, *)
        .returning(toFuture(response))

    def mockUserCapServiceUpdate(result: Either[String, Unit]) = {
      (userCapService.update()(_: ExecutionContext))
        .expects(*)
        .returning(result.fold[Future[Unit]](e ⇒ Future.failed(new Exception(e)), _ ⇒ Future.successful(())))
    }

    def mockEligibilityCheckerService(nino: NINO)(result: Either[String, EligibilityCheckResponse]): Unit =
      (helpToSaveService.getEligibility(_: NINO, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, routes.HelpToSaveController.checkEligibility(nino).url, *, *)
        .returning(EitherT.fromEither[Future](result))

    def mockEmailDelete(nino: NINO)(result: Either[String, Unit]): Unit =
      (emailStore.delete(_: NINO)(_: ExecutionContext))
        .expects(nino, *)
        .returning(EitherT.fromEither[Future](result))

    def mockBarsService(barsRequest: BarsRequest, path: String)(result: Either[String, Boolean]): Unit =
      (barsService.validate(_: BarsRequest, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(barsRequest, path, *, *)
        .returning(Future.successful(result))
  }

  "The HelpToSaveController" when {

    "create account" must {

      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreInsert("nino", false, Some(7), "Digital")(Right(()))
          inAnyOrder {
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital"), "nino")
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED

        // allow time for asynchronous calls to be made
        Thread.sleep(1000L)
      }

      "create account if the request is valid NSIUserInfo json even if updating the enrolment store fails" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreInsert("nino", false, Some(7), "Digital")(Left("error!"))
          inAnyOrder {
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital"), "nino")
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account but dont call ITMP to set the flag if the source is Stride-Manual" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreInsert("nino", true, Some(7), "Stride-Manual")(Right(()))
          inAnyOrder {
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Stride-Manual"), "nino")
          }
        }

        val body = validCreateAccountRequestPayload().as[JsObject] ++ Json.obj("source" → "Stride-Manual")
        val result = controller.createAccount()(FakeRequest().withJsonBody(body))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account if the request is valid NSIUserInfo json even if updating the user counts fails" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED))
          mockEnrolmentStoreInsert("nino", false, Some(7), "Digital")(Right(()))
          inAnyOrder {
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
            mockUserCapServiceUpdate(Left(""))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital"), "nino")
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED

        // allow time for asynchronous calls to be made
        Thread.sleep(1000L)
      }

      "delete any existing emails of DE users before creating the account" in new TestApparatus {
        val payloadDE = validNSIUserInfo.copy(contactDetails = validNSIUserInfo.contactDetails.copy(communicationPreference = "00"))
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockEmailDelete("nino")(Right(()))
          mockCreateAccount(payloadDE)(HttpResponse(CREATED))
          mockEnrolmentStoreInsert("nino", false, Some(7), "Digital")(Right(()))
          inAnyOrder {
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(payloadDE, "Digital"), "nino")
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload("00")))

        status(result)(10.seconds) shouldBe CREATED

      }

      "handle any unexpected mongo errors during deleting emails of DE users" in new TestApparatus {
        val payloadDE = validNSIUserInfo.copy(contactDetails = validNSIUserInfo.contactDetails.copy(communicationPreference = "00"))
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockEmailDelete("nino")(Left("mongo error"))
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload("00")))

        status(result)(10.seconds) shouldBe INTERNAL_SERVER_ERROR

      }

      "return bad request response if the request body is not a valid CreateAccountRequest json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val requestBody = Json.parse(createAccountJson("\"123456\""))
        val result = controller.createAccount()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result = controller.createAccount()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetail":""}"""
      }

      "handle 409 response from proxy" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CONFLICT))
          mockEnrolmentStoreInsert("nino", false, Some(7), "Digital")(Right(()))
          inAnyOrder {
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", true)(Right(()))
          }
        }

        val result = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CONFLICT
        // allow time for asynchronous calls to mocks to be made
        Thread.sleep(1000L)
      }

    }

    "update email" must {
      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        mockUpdateEmail(validUpdateAccountRequest.payload)(HttpResponse(OK))

        val result = controller.updateEmail()(FakeRequest().withJsonBody(validUserInfoPayload.as[JsObject] - "version" - "systemId"))

        status(result)(10.seconds) shouldBe OK
      }

      "return bad request response if the request body is not a valid NSIUserInfo json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val requestBody = Json.parse(payloadJson("\"123456\""))
        val result = controller.updateEmail()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
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
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        mockEligibilityCheckerService(nino)(Left("The Eligibility Check service is unavailable"))

        val result = doRequest(controller)
        status(result) shouldBe 500
      }

      "return the eligibility status returned from the eligibility check service if " +
        "successful" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          val eligibility = EligibilityCheckResponse(EligibilityCheckResult("x", 0, "y", 0), Some(123.45))
          mockEligibilityCheckerService(nino)(Right(eligibility))

          val result = doRequest(controller)
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(eligibility)
        }
    }

    "validating bank details" must {

      val barsRequest = BarsRequest(nino, "123456", "02012345")
      val url = s"/$nino/validate-bank-details"

      "handle send success response if the details are valid or invalid" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockBarsService(barsRequest, url)(Right(true))
        }

        val result = controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson(barsRequest)))
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.parse("""{"isValid":true}""")
      }

      "handle invalid json from the request" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result = controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson("""{"invalid":"barsRequest"}""")))
        status(result) shouldBe 400
      }

      "handle No json from the request" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result = controller.doBarsCheck()(FakeRequest("POST", url))
        status(result) shouldBe 400
      }

      "handle unexpected errors" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockBarsService(barsRequest, url)(Left("unexpected error"))
        }

        val result = controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson(barsRequest)))
        status(result) shouldBe 500
      }
    }
  }

}
