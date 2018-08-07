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

import java.time.{LocalDate, YearMonth}
import java.util.UUID

import org.scalatest.EitherValues
import play.api.libs.json.{JsObject, Json, Writes}
import play.mvc.Http.Status._
import uk.gov.hmrc.helptosave.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosave.models.account._
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
  val updateEmailURL: String = "http://localhost:7005/help-to-save-proxy/update-email"

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

  private def mockUpdateEmailResponse(userInfo: NSIUserInfo)(response: Future[HttpResponse]) =
    (mockHttp.put(_: String, _: NSIUserInfo, _: Map[String, String])(_: Writes[NSIUserInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(updateEmailURL, userInfo, Map.empty[String, String], *, *, *)
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

  private def mockGetTransactionsResponse(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, Map.empty[String, String], *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("Test exception message")))(Future.successful))

  "The HelpToSaveProxyConnector" when {
    "creating account" must {

      val source = "source"

      "handle 201 response from the help-to-save-proxy" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(CREATED)))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe CREATED
      }

      "handle 409 response from the help-to-save-proxy" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(CONFLICT)))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe CONFLICT
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
              "errorDetail"  : "boom"
            }""")
      }
    }

    "update email" must {

      "handle 200 response from the help-to-save-proxy" in {
        mockUpdateEmailResponse(userInfo)(toFuture(HttpResponse(OK)))
        val result = await(proxyConnector.updateEmail(userInfo))

        result.status shouldBe OK
      }
    }

    "querying DWP for UC Claimant checks" must {

      val txnId = UUID.randomUUID()
      val nino = "AE123456C"
      val threshold = 650.0

      val url = s"http://localhost:7005/help-to-save-proxy/uc-claimant-check?nino=$nino&transactionId=$txnId&threshold=$threshold"

      "handle success response from proxy" in {

          def test(uCResponse: UCResponse): Unit = {

            mockUCClaimantCheck(url)(Some(HttpResponse(OK, Some(Json.toJson(uCResponse)))))

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)

            result shouldBe Right(uCResponse)
          }

        test(UCResponse(true, Some(true)))
        test(UCResponse(true, Some(false)))
        test(UCResponse(false, None))

      }

      "handle bad_request response from frontend" in {

        mockUCClaimantCheck(url)(Some(HttpResponse(BAD_REQUEST)))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)

        result shouldBe Left("Received unexpected status(400) from UniversalCredit check")
      }

      "handles failures due to invalid json" in {

          def test(json: String) = {
            mockUCClaimantCheck(url)(Some(HttpResponse(OK, Some(Json.parse(json)))))

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)

            result.left.value contains "unable to parse UCResponse from proxy"
          }

        test("""{"foo": "bar"}""")
        test("""{"ucClaimant":"foo", "withinThreshold":"bar"}""")
      }

      "handle unexpected errors" in {

        mockFailUCClaimantCheck(url)(new RuntimeException("boom"))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)

        result shouldBe Left("Call to UniversalCredit check unsuccessful: boom")
      }
    }

    "retrieving NsiAccount" must {

      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-account.version")

      val queryString = s"nino=$nino&correlationId=$correlationId&version=$version&systemId=$systemId"

      val getAccountUrl: String = s"http://localhost:7005/help-to-save-proxy/nsi-services/account?$queryString"

      val nsiAccountJson = Json.parse(
        """
          |{
          |  "accountNumber": "AC01",
          |  "accountBalance": "200.34",
          |  "accountClosedFlag": "",
          |  "accountBlockingCode": "00",
          |  "clientBlockingCode": "00",
          |  "currentInvestmentMonth": {
          |    "investmentRemaining": "15.50",
          |    "investmentLimit": "50.00",
          |    "endDate": "2018-02-28"
          |  },
          |  "clientForename":"Testforename",
          |  "clientSurname":"Testsurname",
          |  "emailAddress":"test@example.com",
          |  "terms": [
          |     {
          |       "termNumber":2,
          |       "startDate":"2020-01-01",
          |       "endDate":"2021-12-31",
          |       "bonusEstimate":"67.00",
          |       "bonusPaid":"0.00"
          |    },
          |    {
          |       "termNumber":1,
          |       "startDate":"2018-01-01",
          |       "endDate":"2019-12-31",
          |       "bonusEstimate":"123.45",
          |       "bonusPaid":"123.45"
          |    }
          |  ]
          |}
        """.stripMargin).as[JsObject]

      val account = Account(
        YearMonth.of(2018, 1),
        "AC01", false,
        Blocking(false, false),
        200.34,
        34.50,
        15.50,
        50.00,
        LocalDate.parse("2018-02-28"),
        "Testforename",
        "Testsurname",
        Some("test@example.com"),
        List(
          BonusTerm(bonusEstimate          = 123.45, bonusPaid = 123.45, endDate = LocalDate.parse("2019-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2020-01-01")),
          BonusTerm(bonusEstimate          = 67.00, bonusPaid = 0.00, endDate = LocalDate.parse("2021-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2022-01-01"))
        ),
        None,
        None)

      "handle success response with Accounts having Terms" in {
        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(nsiAccountJson))))

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)

        result shouldBe Right(Some(account))
      }

      "throw error when there are no Terms in the json" in {
        val json = nsiAccountJson + ("terms" -> Json.arr())

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(json))))
        mockPagerDutyAlert("Could not parse JSON in the getAccount response")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)

        result shouldBe Left("Could not parse getNsiAccount response, received 200 (OK), error=[Bonus terms list returned by NS&I was empty]")
      }

      "throw error when the getAccount response json missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
        val json = nsiAccountJson - "accountBalance"

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(json))))
        mockPagerDutyAlert("Could not parse JSON in the getAccount response")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)

        result.isLeft shouldBe true
      }

      "succeed when the NS&I response omits the emailAddress optional field" in {
        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(200, Some(nsiAccountJson - "emailAddress"))))

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)

        result shouldBe Right(Some(account.copy(accountHolderEmail = None)))
      }

      "handle non 200 responses from help-to-save-proxy" in {
        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(400)))
        mockPagerDutyAlert("Received unexpected http status in response to getAccount")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)
        result shouldBe Left("Received unexpected status(400) from getNsiAccount call")
      }

      "handle unexpected server errors" in {
        mockGetAccountResponse(getAccountUrl)(None)
        mockPagerDutyAlert("Failed to make call to getAccount")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)
        result.isLeft shouldBe true
      }

      "handle the case where an account does not exist" in {
        val errorResponse =
          Json.parse(
            s"""
              |{
              |  "errors" : [
              |    {
              |      "errorMessageId" : "id",
              |      "errorMessage"   : "message",
              |      "errorDetail"    : "detail"
              |    },
              |    {
              |      "errorMessageId" : "${appConfig.runModeConfiguration.underlying.getString("nsi.no-account-error-message-id")}",
              |      "errorMessage"   : "Oh no!",
              |      "errorDetail"    : "Account doesn't exist"
              |    }
              |  ]
              |}
          """.stripMargin)

        mockGetAccountResponse(getAccountUrl)(Some(HttpResponse(400, Some(errorResponse))))

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId).value)
        result shouldBe Right(None)
      }

      "handle characters in correlationId and systemId that need to be escaped" in {
        val needsEscapingCorrelationId = "a&b"
        val needsEscapingSystemId = "system<>&id"
        val queryString = s"nino=$nino&correlationId=${java.net.URLEncoder.encode(needsEscapingCorrelationId, "UTF-8")}&version=$version" +
          s"&systemId=${java.net.URLEncoder.encode(needsEscapingSystemId, "UTF-8")}"

        val needsEscapingGetAccountUrl: String = s"http://localhost:7005/help-to-save-proxy/nsi-services/account?$queryString"

        mockGetAccountResponse(needsEscapingGetAccountUrl)(Some(HttpResponse(200, Some(nsiAccountJson))))

        val result = await(proxyConnector.getAccount(nino, needsEscapingSystemId, needsEscapingCorrelationId).value)

        result shouldBe Right(Some(account))
      }

    }

    "retrieving transactions" must {
      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-transactions.version")

      val queryString = s"nino=$nino&correlationId=$correlationId&version=$version&systemId=$systemId"

      val getTransactionsUrl: String = s"http://localhost:7005/help-to-save-proxy/nsi-services/transactions?$queryString"

        def transactionMetricChanges[T](body: ⇒ T): (T, Long, Long) = {
          val timerCountBefore = mockMetrics.getTransactionsTimer.getCount
          val errorCountBefore = mockMetrics.getTransactionsErrorCounter.getCount
          val result = body
          (result, mockMetrics.getTransactionsTimer.getCount - timerCountBefore, mockMetrics.getTransactionsErrorCounter.getCount - errorCountBefore)
        }

      "handle success response by translating transactions from NS&I domain into MDTP domain" in {
        val json = Json.parse(
          """{
            |  "transactions": [
            |    {
            |      "sequence": "1",
            |      "amount": "11.50",
            |      "operation": "C",
            |      "description": "Debit card online deposit",
            |      "transactionReference": "A1A11AA1A00A0034",
            |      "transactionDate": "2017-11-20",
            |      "accountingDate": "2017-11-20"
            |    },
            |    {
            |      "sequence": "2",
            |      "amount": "1.01",
            |      "operation": "D",
            |      "description": "BACS payment",
            |      "transactionReference": "A1A11AA1A00A000I",
            |      "transactionDate": "2017-11-27",
            |      "accountingDate": "2017-11-27"
            |    },
            |    {
            |      "sequence": "3",
            |      "amount": "1.11",
            |      "operation": "D",
            |      "description": "BACS payment",
            |      "transactionReference": "A1A11AA1A00A000G",
            |      "transactionDate": "2017-11-27",
            |      "accountingDate": "2017-11-27"
            |    },
            |    {
            |      "sequence": "4",
            |      "amount": "1.11",
            |      "operation": "C",
            |      "description": "Reinstatement Adjustment",
            |      "transactionReference": "A1A11AA1A00A000G",
            |      "transactionDate": "2017-11-27",
            |      "accountingDate": "2017-12-04"
            |    },
            |    {
            |      "sequence": "5",
            |      "amount": "50.00",
            |      "operation": "C",
            |      "description": "Debit card online deposit",
            |      "transactionReference": "A1A11AA1A00A0059",
            |      "transactionDate": "2018-04-10",
            |      "accountingDate": "2018-04-10"
            |    }
            |  ]
            |}
            |""".stripMargin
        )

        mockGetTransactionsResponse(getTransactionsUrl)(Some(HttpResponse(200, Some(json))))

        val (result, timerMetricChange, errorMetricChange) = transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Right(Some(Transactions(Seq(
          Transaction(Credit, BigDecimal("11.50"), LocalDate.parse("2017-11-20"), LocalDate.parse("2017-11-20"), "Debit card online deposit", "A1A11AA1A00A0034", BigDecimal("11.50")),
          Transaction(Debit, BigDecimal("1.01"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-11-27"), "BACS payment", "A1A11AA1A00A000I", BigDecimal("10.49")),
          Transaction(Debit, BigDecimal("1.11"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-11-27"), "BACS payment", "A1A11AA1A00A000G", BigDecimal("9.38")),
          Transaction(Credit, BigDecimal("1.11"), LocalDate.parse("2017-11-27"), LocalDate.parse("2017-12-04"), "Reinstatement Adjustment", "A1A11AA1A00A000G", BigDecimal("10.49")),
          Transaction(Credit, BigDecimal(50), LocalDate.parse("2018-04-10"), LocalDate.parse("2018-04-10"), "Debit card online deposit", "A1A11AA1A00A0059", BigDecimal("60.49"))
        ))))
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 0
      }

      "return an error when the GET transaction JSON is missing fields that are required according to get_transactions_by_nino_RESP_schema_V1.0.json" in {
        val json = Json.parse(
          // invalid because sequence is missing from first transaction
          """{
            |  "transactions": [
            |    {
            |      "amount": "11.50",
            |      "operation": "C",
            |      "description": "Debit card online deposit",
            |      "transactionReference": "A1A11AA1A00A0034",
            |      "transactionDate": "2017-11-20",
            |      "accountingDate": "2017-11-20"
            |    }
            |  ]
            |}
            |""".stripMargin
        )

        mockGetTransactionsResponse(getTransactionsUrl)(Some(HttpResponse(200, Some(json))))
        mockPagerDutyAlert("Could not parse get transactions response")

        val (result, timerMetricChange, errorMetricChange) = transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Left("Could not parse transactions response from NS&I, received 200 (OK), error=[Could not parse http response JSON: /transactions(0)/sequence: [error.path.missing]]")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "return an error when Transaction validation fails due to invalid field values" in {
        val json = Json.parse(
          // invalid because sequence is missing from first transaction
          """{
            |  "transactions": [
            |    {
            |      "sequence": "1",
            |      "amount": "11.50",
            |      "operation": "bad",
            |      "description": "Debit card online deposit",
            |      "transactionReference": "A1A11AA1A00A0034",
            |      "transactionDate": "2017-11-20",
            |      "accountingDate": "2017-11-20"
            |    }
            |  ]
            |}
            |""".stripMargin
        )

        mockGetTransactionsResponse(getTransactionsUrl)(Some(HttpResponse(200, Some(json))))
        mockPagerDutyAlert("Could not parse get transactions response")

        val (result, timerMetricChange, errorMetricChange) = transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Left("""Could not parse transactions response from NS&I, received 200 (OK), error=[Unknown value for operation: "bad"]""")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "handle non 200 responses from help-to-save-proxy" in {
        mockGetTransactionsResponse(getTransactionsUrl)(Some(HttpResponse(400)))
        mockPagerDutyAlert("Received unexpected http status in response to get transactions")

        val (result, timerMetricChange, errorMetricChange) = transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
        result shouldBe Left("Received unexpected status(400) from get transactions call")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "handle unexpected server errors" in {
        mockGetTransactionsResponse(getTransactionsUrl)(None)
        mockPagerDutyAlert("Failed to make call to get transactions")

        val (result, timerMetricChange, errorMetricChange) = transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
        result shouldBe Left("Call to get transactions unsuccessful: Test exception message")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "handle the case where an account does not exist by returning Right(None)" in {
        val errorResponse =
          Json.parse(
            s"""
              |{
              |  "errors" : [
              |    {
              |      "errorMessageId" : "id",
              |      "errorMessage"   : "message",
              |      "errorDetail"    : "detail"
              |    },
              |    {
              |      "errorMessageId" : "${appConfig.runModeConfiguration.underlying.getString("nsi.no-account-error-message-id")}",
              |      "errorMessage"   : "Oh no!",
              |      "errorDetail"    : "Account doesn't exist"
              |    }
              |  ]
              |}
          """.stripMargin)

        mockGetTransactionsResponse(getTransactionsUrl)(Some(HttpResponse(400, Some(errorResponse))))

        val result = await(proxyConnector.getTransactions(nino, systemId, correlationId).value)
        result shouldBe Right(None)
      }

      "handle characters in correlationId and systemId that need to be escaped" in {
        val needsEscapingCorrelationId = "a&b"
        val needsEscapingSystemId = "system<>&id"
        val queryString = s"nino=$nino&correlationId=${java.net.URLEncoder.encode(needsEscapingCorrelationId, "UTF-8")}&version=$version" +
          s"&systemId=${java.net.URLEncoder.encode(needsEscapingSystemId, "UTF-8")}"

        val needsEscapingGetTransactionsUrl: String = s"http://localhost:7005/help-to-save-proxy/nsi-services/transactions?$queryString"

        val json = Json.parse(
          """{
            |  "transactions": [
            |    {
            |      "sequence": "1",
            |      "amount": "11.50",
            |      "operation": "C",
            |      "description": "Debit card online deposit",
            |      "transactionReference": "A1A11AA1A00A0034",
            |      "transactionDate": "2017-11-20",
            |      "accountingDate": "2017-11-20"
            |    }
            |  ]
            |}
            |""".stripMargin
        )

        mockGetTransactionsResponse(needsEscapingGetTransactionsUrl)(Some(HttpResponse(200, Some(json))))

        val result = await(proxyConnector.getTransactions(nino, needsEscapingSystemId, needsEscapingCorrelationId).value)

        result shouldBe Right(Some(Transactions(Seq(
          Transaction(Credit, BigDecimal("11.50"), LocalDate.parse("2017-11-20"), LocalDate.parse("2017-11-20"), "Debit card online deposit", "A1A11AA1A00A0034", BigDecimal("11.50"))
        ))))
      }
    }
  }
}
