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

package uk.gov.hmrc.helptosave.connectors

import java.time.LocalDate
import java.util.UUID

import play.api.libs.json.{Json, Writes}
import play.mvc.Http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.helptosave.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosave.models.{NSIUserInfo, UCResponse}
import uk.gov.hmrc.helptosave.util.toFuture
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class HelpToSaveProxyConnectorSpec extends TestSupport {

  lazy val proxyConnector = new HelpToSaveProxyConnectorImpl(mockHttp)
  val createAccountURL: String = "http://localhost:7005/help-to-save-proxy/create-account"

  val userInfo: NSIUserInfo =
    NSIUserInfo(
      "forename",
      "surname",
      LocalDate.now(),
      "nino",
      ContactDetails("address1", "address2", Some("address3"), Some("address4"), Some("address5"), "postcode", Some("GB"), Some("phoneNumber"), "commPref"),
      "regChannel"
    )

  private def mockCreateAccountResponse(userInfo: NSIUserInfo)(response: Future[HttpResponse]) =
    (mockHttp.post(_: String, _: NSIUserInfo, _: Map[String, String])(_: Writes[NSIUserInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountURL, userInfo, Map.empty[String, String], *, *, *)
      .returning(response)

  private def mockFailCreateAccountResponse(userInfo: NSIUserInfo)(ex: Exception) =
    (mockHttp.post(_: String, _: NSIUserInfo, _: Map[String, String])(_: Writes[NSIUserInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountURL, userInfo, Map.empty[String, String], *, *, *)
      .returning(Future.failed(ex))

  private def mockUCClaimantCheck(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  private def mockFailUCClaimantCheck(url: String)(ex: Exception) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(Future.failed(ex))

  "The FrontendConnector" when {
    "creating account" must {
      "handle success response from frontend" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(CREATED)))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe CREATED
      }

      "handle bad_request response from frontend" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(BAD_REQUEST)))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe BAD_REQUEST
      }

      "handle unexpected errors" in {

        mockFailCreateAccountResponse(userInfo)(new RuntimeException("boom"))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe INTERNAL_SERVER_ERROR
        Json.parse(result.body) shouldBe
          Json.parse(
            """{
              "errorMessageId" : "",
              "errorMessage" : "unexpected error from proxy during /create-de-account",
              "errorDetails" : "boom"
            }""")
      }
    }

    "querying DWP for UC Claimant checks" must {

      val txnId = UUID.randomUUID()
      val nino = "AE123456C"
      val uCResponse = UCResponse("Y", "Y")

      val url = s"http://localhost:7005/help-to-save-proxy/uc-claimant-check?nino=$nino&transactionId=$txnId"

      "handle success response from frontend" in {

        mockUCClaimantCheck(url)(Some(HttpResponse(OK, Some(Json.toJson(uCResponse)))))

        val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId).value, 5.seconds)

        result shouldBe Right(uCResponse)
      }

      "handle bad_request response from frontend" in {

        mockUCClaimantCheck(url)(Some(HttpResponse(BAD_REQUEST)))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId).value)

        result shouldBe Left("Received unexpected status(400) from UniversalCredit check")
      }

      "handle unexpected errors" in {

        mockFailUCClaimantCheck(url)(new RuntimeException("boom"))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId).value)

        result shouldBe Left("Call to UniversalCredit check unsuccessful: boom")
      }
    }
  }
}
