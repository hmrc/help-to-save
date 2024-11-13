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

package uk.gov.hmrc.helptosave.controllers

import cats.data.EitherT
import cats.instances.future._
import org.apache.pekko.util.Timeout
import org.mockito.ArgumentMatchersSugar.*
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
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
import uk.gov.hmrc.http.HttpResponse

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration._

// scalastyle:off magic.number
class HelpToSaveControllerSpec extends AuthSupport with TestEnrolmentBehaviour {

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)
  val returnHeaders: Map[NINO, Seq[NINO]] = Map[String, Seq[String]]()
  class TestApparatus {
    val proxyConnector: HelpToSaveProxyConnector = mock[HelpToSaveProxyConnector]
    val userCapService: UserCapService = mock[UserCapService]
    val mockAuditor: HTSAuditor = mock[HTSAuditor]
    val emailStore: EmailStore = mock[EmailStore]
    val barsService: BarsService = mock[BarsService]

    val controller = new HelpToSaveController(
      enrolmentStore,
      emailStore,
      proxyConnector,
      userCapService,
      helpToSaveService,
      mockAuthConnector,
      mockAuditor,
      barsService,
      testCC)

    val accountNumber: Option[NINO] = Some("AC01")

    def mockSendAuditEvent(event: HTSEvent, nino: String) =
      mockAuditor
        .sendEvent(event, nino)(*)
        .doesNothing()

    def mockCreateAccount(expectedPayload: NSIPayload)(response: HttpResponse) =
      proxyConnector
        .createAccount(expectedPayload)(*, *)
        .returns(toFuture(Right(response)))

    def mockUpdateEmail(expectedPayload: NSIPayload)(response: HttpResponse) =
      proxyConnector
        .updateEmail(expectedPayload)(*, *)
        .returns(toFuture(Right(response)))

    def mockUserCapServiceUpdate(result: Either[String, Unit]) =
      userCapService
        .update()(*)
        .returns(result.fold[Future[Unit]](e => Future.failed(new Exception(e)), _ => Future.successful(())))

    def mockEmailDelete(nino: NINO)(result: Either[String, Unit]): Unit =
      emailStore
        .delete(nino)(*)
        .returns(EitherT.fromEither[Future](result))

    def mockBarsService(barsRequest: BankDetailsValidationRequest)(
      result: Either[String, BankDetailsValidationResult]): Unit =
      barsService
        .validate(barsRequest)(*, *, *)
        .returns(Future.successful(result))
  }

  "The HelpToSaveController" when {

    "create account" must {

      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", itmpFlag = true)(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital", detailsManuallyEntered = false), "nino")

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED

        // allow time for asynchronous calls to be made
        Thread.sleep(1000L)
      }

      "create account if the request is valid NSIUserInfo json and the details have been manually entered" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", itmpFlag = true)(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital", detailsManuallyEntered = true), "nino")

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload(detailsManuallyEntered = true)))

        status(result)(10.seconds) shouldBe CREATED

        // allow time for asynchronous calls to be made
        Thread.sleep(1000L)
      }

      "create account if the request is valid NSIUserInfo json even if updating the enrolment store fails" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Left("error!"))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital", detailsManuallyEntered = false), "nino")

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account but dont call ITMP to set the flag if the source is Stride-Manual" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = true, Some(7), "Stride-Manual", accountNumber)(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Stride-Manual", detailsManuallyEntered = false), "nino")

        val body: JsObject = validCreateAccountRequestPayload().as[JsObject] ++ Json.obj("source" -> "Stride-Manual")
        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(body))

        status(result)(10.seconds) shouldBe CREATED
      }

      "create account if the request is valid NSIUserInfo json even if updating the user counts fails" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", itmpFlag = true)(Right(()))
            mockUserCapServiceUpdate(Left(""))
            mockSendAuditEvent(AccountCreated(validNSIUserInfo, "Digital", detailsManuallyEntered = false), "nino")

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CREATED

        // allow time for asynchronous calls to be made
        Thread.sleep(1000L)
      }

      "delete any existing emails of DE users before creating the account" in new TestApparatus {
        val payloadDE: NSIPayload =
          validNSIUserInfo.copy(contactDetails = validNSIUserInfo.contactDetails.copy(communicationPreference = "00"))
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockEmailDelete("nino")(Right(()))
          mockCreateAccount(payloadDE)(HttpResponse(CREATED, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockUserCapServiceUpdate(Right(()))
            mockSendAuditEvent(AccountCreated(payloadDE, "Digital", detailsManuallyEntered = false), "nino")

        val result: Future[Result] =
          controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload(detailsManuallyEntered = false, "00")))

        status(result)(10.seconds) shouldBe CREATED

      }

      "handle any unexpected mongo errors during deleting emails of DE users" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockEmailDelete("nino")(Left("mongo error"))

        val result: Future[Result] =
          controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload(detailsManuallyEntered = false, "00")))

        status(result)(10.seconds) shouldBe INTERNAL_SERVER_ERROR

      }

      "return bad request response if the request body is not a valid CreateAccountRequest json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val requestBody: JsValue = Json.parse(createAccountJson("\"123456\"", detailsManuallyEntered = false))
        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result: Future[Result] = controller.createAccount()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result)
          .toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetail":""}"""
      }

      "handle 409 response from proxy" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CONFLICT, Json.toJson(account), returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", accountNumber)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", itmpFlag = true)(Right(()))

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CONFLICT
        // allow time for asynchronous calls to mocks to be made
        Thread.sleep(1000L)
      }

      "handle 409 response with no account number from proxy" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockCreateAccount(validNSIUserInfo)(HttpResponse(CONFLICT, "", returnHeaders))
          mockEnrolmentStoreInsert("nino", itmpFlag = false, Some(7), "Digital", None)(Right(()))
            mockSetFlag("nino")(Right(()))
            mockEnrolmentStoreUpdate("nino", itmpFlag = true)(Right(()))

        val result: Future[Result] = controller.createAccount()(FakeRequest().withJsonBody(validCreateAccountRequestPayload()))

        status(result)(10.seconds) shouldBe CONFLICT
        // allow time for asynchronous calls to mocks to be made
        Thread.sleep(1000L)
      }

    }

    "update email" must {
      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        mockUpdateEmail(validUpdateAccountRequest.payload)(HttpResponse(OK, ""))

        val result: Future[Result] = controller.updateEmail()(
          FakeRequest().withJsonBody(validUserInfoPayload.as[JsObject] - "version" - "systemId"))

        status(result)(10.seconds) shouldBe OK
      }

      "return bad request response if the request body is not a valid NSIUserInfo json" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val requestBody: JsValue = Json.parse(payloadJson("\"123456\""))
        val result: Future[Result] = controller.updateEmail()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result: Future[Result] = controller.updateEmail()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result)
          .toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetail":""}"""
      }
    }

    "validating bank details" must {

      val barsRequest = BankDetailsValidationRequest(nino, "123456", "02012345")
      val url = s"/$nino/validate-bank-details"

      "handle send success response if the details are valid or invalid" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockBarsService(barsRequest)(Right(BankDetailsValidationResult(isValid = true, sortCodeExists = true)))

        val result: Future[Result] = controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson(barsRequest)))
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.parse("""{"isValid":true, "sortCodeExists":true}""")
      }

      "handle invalid json from the request" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result: Future[Result] =
          controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson("""{"invalid":"barsRequest"}""")))
        status(result) shouldBe 400
      }

      "handle No json from the request" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
        val result: Future[Result] = controller.doBarsCheck()(FakeRequest("POST", url))
        status(result) shouldBe 400
      }

      "handle unexpected errors" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, EmptyRetrieval)(Right(()))
          mockBarsService(barsRequest)(Left("unexpected error"))

        val result: Future[Result] = controller.doBarsCheck()(FakeRequest("POST", url).withJsonBody(Json.toJson(barsRequest)))
        status(result) shouldBe 500
      }
    }
  }

}
