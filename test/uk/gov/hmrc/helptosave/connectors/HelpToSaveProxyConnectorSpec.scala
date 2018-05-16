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

import org.scalatest.EitherValues
import play.api.libs.json.{Json, Writes}
import play.mvc.Http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.helptosave.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosave.models.account.{Account, Blocking, BonusTerm}
import uk.gov.hmrc.helptosave.models.{NSIUserInfo, UCResponse}
import uk.gov.hmrc.helptosave.util.toFuture
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveProxyConnectorSpec extends TestSupport with MockPagerDuty with EitherValues {

  lazy val proxyConnector = new HelpToSaveProxyConnectorImpl(mockHttp, mockMetrics, mockPagerDuty)
  val createAccountURL: String = "http://localhost:7005/help-to-save-proxy/create-account"

  val userInfo: NSIUserInfo =
    NSIUserInfo(
      "forename",
      "surname",
      LocalDate.now(),
      "nino",
      ContactDetails("address1", "address2", Some("address3"), Some("address4"), Some("address5"), "postcode", Some("GB"), Some("phoneNumber"), Some("email"), "commPref"),
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

  private def mockGetAccountResponse(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, Map.empty[String, String], *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  "The HelpToSaveProxyConnector" when {
    "creating account" must {
      "handle success response from the help-to-save-proxy" in {

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

      val url = s"http://localhost:7005/help-to-save-proxy/uc-claimant-check?nino=$nino&transactionId=$txnId"

      "handle success response from proxy" in {

          def test(uCResponse: UCResponse): Unit = {

            mockUCClaimantCheck(url)(Some(HttpResponse(OK, Some(Json.toJson(uCResponse)))))

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId).value, 5.seconds)

            result shouldBe Right(uCResponse)
          }

        test(UCResponse(true, Some(true)))
        test(UCResponse(true, Some(false)))
        test(UCResponse(false, None))

      }

      "handle bad_request response from frontend" in {

        mockUCClaimantCheck(url)(Some(HttpResponse(BAD_REQUEST)))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId).value)

        result shouldBe Left("Received unexpected status(400) from UniversalCredit check")
      }

      "handles failures due to invalid json" in {

          def test(json: String) = {
            mockUCClaimantCheck(url)(Some(HttpResponse(OK, Some(Json.parse(json)))))

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId).value, 5.seconds)

            result.left.value contains "unable to parse UCResponse from proxy"
          }

        test("""{"foo": "bar"}""")
        test("""{"ucClaimant":"foo", "withinThreshold":"bar"}""")
      }

      "handle unexpected errors" in {

        mockFailUCClaimantCheck(url)(new RuntimeException("boom"))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId).value)

        result shouldBe Left("Call to UniversalCredit check unsuccessful: boom")
      }
    }

    "retrieving NsiAccount" must {

      val nino = randomNINO()
      val correlationId = UUID.randomUUID()

      val queryString = s"nino=$nino&correlationId=$correlationId&systemId=123"

      val getAccountUrl: String = s"http://localhost:7005/help-to-save-proxy/nsi-services/account?$queryString"

      "handle success response with Accounts having Terms" in {

        val json = Json.parse(
          """
            |{
            |  "accountBalance": "200.34",
            |  "accountClosedFlag": "",
            |  "accountBlockingCode": "00",
            |  "clientBlockingCode": "00",
            |  "currentInvestmentMonth": {
            |    "investmentRemaining": "15.50",
            |    "investmentLimit": "50.00",
            |    "endDate": "2018-02-28"
            |  },
            |  "terms": [
            |     {
            |       "termNumber":2,
            |       "endDate":"2021-12-31",
            |       "bonusEstimate":"67.00",
            |       "bonusPaid":"0.00"
            |    },
            |    {
            |       "termNumber":1,
            |       "endDate":"2019-12-31",
            |       "bonusEstimate":"123.45",
            |       "bonusPaid":"123.45"
            |    }
            |  ]
            |}
          """.stripMargin)

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(json))))

        val result = await(proxyConnector.getAccount(nino, queryString).value)

        result shouldBe Right(Some(Account(false,
          Blocking(false),
          200.34,
          34.50,
          15.50,
          50.00,
          LocalDate.parse("2018-02-28"),
          List(BonusTerm(123.45, 123.45, LocalDate.parse("2019-12-31"), LocalDate.parse("2020-01-01")),
               BonusTerm(67.00, 0.00, LocalDate.parse("2021-12-31"), LocalDate.parse("2022-01-01"))),
          None,
          None)
        ))
      }

      "handle success response when the Account is cloned and there are no Terms in the json" in {
        val json = Json.parse(
          """
            |{
            |  "accountBalance": "0.00",
            |  "accountClosedFlag": "C",
            |  "accountBlockingCode": "T1",
            |  "clientBlockingCode": "client blocking test",
            |  "accountClosureDate": "2018-04-09",
            |  "accountClosingBalance": "10.11",
            |  "currentInvestmentMonth": {
            |    "endDate": "2018-04-30",
            |    "investmentRemaining": "12.34",
            |    "investmentLimit": "150.42"
            |  },
            |  "terms": []
            |}
          """.stripMargin)

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(json))))

        val result = await(proxyConnector.getAccount(nino, queryString).value)

        result shouldBe Right(Some(Account(true,
          Blocking(true),
          0.00,
          138.08,
          12.34,
          150.42,
          LocalDate.parse("2018-04-30"),
          List.empty,
          Some(LocalDate.parse("2018-04-09")),
          Some(10.11))
        ))
      }

      "throw error when the getAccount response json missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
        val json = Json.parse(
          // invalid because required field bonusPaid is omitted from first term
          """
            |{
            |  "accountBalance": "123.45",
            |  "currentInvestmentMonth": {
            |    "investmentRemaining": "15.50",
            |    "investmentLimit": "50.00"
            |  },
            |  "terms": [
            |     {
            |       "termNumber":1,
            |       "endDate":"2019-12-31",
            |       "bonusEstimate":"90.99"
            |    },
            |    {
            |       "termNumber":2,
            |       "endDate":"2021-12-31",
            |       "bonusEstimate":"12.00",
            |       "bonusPaid":"00.00"
            |    }
            |  ]
            |}""".stripMargin)

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(json))))
        mockPagerDutyAlert("Could not parse JSON in the getAccount response")

        val result = await(proxyConnector.getAccount(nino, queryString).value)

        result.isLeft shouldBe true
      }

      "handle non 200 responses from help-to-save-proxy" in {
        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(400)))
        mockPagerDutyAlert("Received unexpected http status in response to getAccount")

        val result = await(proxyConnector.getAccount(nino, queryString).value)
        result shouldBe Left("Received unexpected status(400) from getNsiAccount call")
      }

      "handle unexpected server errors" in {
        mockGetAccountResponse(getAccountUrl)(None)
        mockPagerDutyAlert("Failed to make call to getAccount")

        val result = await(proxyConnector.getAccount(nino, queryString).value)
        result.isLeft shouldBe true
      }
    }
  }
}
