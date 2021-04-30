/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
import play.mvc.Http.Status._
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosave.models.account._
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestEnrolmentBehaviour}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

// scalastyle:off magic.number
class HelpToSaveProxyConnectorSpec extends TestEnrolmentBehaviour with MockPagerDuty with EitherValues with HttpSupport {

  val mockAuditor = mock[HTSAuditor]
  val returnHeaders = Map[String, Seq[String]]()
  override val proxyConnector = new HelpToSaveProxyConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, mockAuditor, servicesConfig)
  val createAccountURL: String = "http://localhost:7005/help-to-save-proxy/create-account"
  val updateEmailURL: String = "http://localhost:7005/help-to-save-proxy/update-email"

  val userInfo: NSIPayload =
    NSIPayload(
      "forename",
      "surname",
      LocalDate.now(),
      "nino",
      ContactDetails("address1", "address2", Some("address3"), Some("address4"), Some("address5"), "postcode", Some("GB"), Some("phoneNumber"), Some("email"), "commPref"),
      "regChannel", None, Some("version"), Some("systemId")
    )

  def mockSendAuditEvent(event: GetAccountResultEvent, nino: String) =
    (mockAuditor.sendEvent(_: GetAccountResultEvent, _: String)(_: ExecutionContext))
      .expects(event, nino, *)
      .returning(())

  "The HelpToSaveProxyConnector" when {
    "creating account" must {

      "handle 201 response from the help-to-save-proxy" in {

        mockPost(createAccountURL, Map.empty[String, String], userInfo)(Some(HttpResponse(CREATED, "")))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe CREATED
      }

      "handle 409 response from the help-to-save-proxy" in {

        mockPost(createAccountURL, Map.empty[String, String], userInfo)(Some(HttpResponse(CONFLICT, "")))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe CONFLICT
      }

      "handle bad_request response from frontend" in {

        mockPost(createAccountURL, Map.empty[String, String], userInfo)(Some(HttpResponse(BAD_REQUEST, "")))
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe BAD_REQUEST
      }

      "handle unexpected errors" in {

        mockPost(createAccountURL, Map.empty[String, String], userInfo)(None)
        val result = await(proxyConnector.createAccount(userInfo))

        result.status shouldBe INTERNAL_SERVER_ERROR
        Json.parse(result.body) shouldBe
          Json.parse(
            """{
              "errorMessageId" : "",
              "errorMessage" : "unexpected error from proxy during /create-de-account",
              "errorDetail"  : "Test exception message"
            }""")
      }
    }

    "update email" must {

      "handle 200 response from the help-to-save-proxy" in {
        mockPut(updateEmailURL, userInfo)(Some(HttpResponse(OK, "")))
        val result = await(proxyConnector.updateEmail(userInfo))

        result.status shouldBe OK
      }
    }

    "querying DWP for UC Claimant checks" must {

      val txnId = UUID.randomUUID()
      val nino = "AE123456C"
      val threshold = 650.0

      val url = "http://localhost:7005/help-to-save-proxy/uc-claimant-check"
      val queryParams = Map("nino" → nino, "transactionId" → txnId.toString, "threshold" → threshold.toString)

      "handle success response from proxy" in {

          def test(uCResponse: UCResponse): Unit = {
            withClue(s"For UCResponse $uCResponse:"){
              mockGet(url, queryParams)(Some(HttpResponse(OK, Json.toJson(uCResponse), returnHeaders)))

              val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)

              result shouldBe Right(uCResponse)
            }
          }

        test(UCResponse(true, Some(true)))
        test(UCResponse(true, Some(false)))
        test(UCResponse(false, None))

      }

      "handle bad_request response from frontend" in {
        mockGet(url, queryParams)(Some(HttpResponse(BAD_REQUEST, "")))
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)

        result shouldBe Left("Received unexpected status(400) from UniversalCredit check")
      }

      "handles failures due to invalid json" in {

          def test(json: String) = {
            withClue(s"For json $json:") {
              mockGet(url, queryParams)(Some(HttpResponse(OK, Json.parse(json), returnHeaders)))

              val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)
              result.left.value contains "unable to parse UCResponse from proxy"
            }
          }

        test("""{"foo": "bar"}""")
        test("""{"ucClaimant":"foo", "withinThreshold":"bar"}""")
      }

      "handle unexpected errors" in {
        mockGet(url, queryParams)(None)
        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)

        result shouldBe Left("Call to UniversalCredit check unsuccessful: Test exception message")
      }
    }

    "retrieving NsiAccount" must {

      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-account.version")

      val getAccountUrl: String = "http://localhost:7005/help-to-save-proxy/nsi-services/account"
      val queryParameters = Map("nino" → nino, "correlationId" → correlationId, "version" → version, "systemId" → systemId)

      val path = s"/help-to-save/$nino/account?nino=$nino&systemId=$systemId&correlationId=$correlationId"
        def event(accountJson: JsValue = nsiAccountJson) = GetAccountResultEvent(GetAccountResult(nino, accountJson), path)

      "handle success response with Accounts having Terms" in {
        mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(200, nsiAccountJson, returnHeaders)))
        mockSendAuditEvent(event(), nino)

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)

        result shouldBe Right(Some(account))
      }

      "throw error when there are no Terms in the json" in {
        val json = nsiAccountJson + ("terms" -> Json.arr())

        inSequence {
          mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))
          mockSendAuditEvent(event(json), nino)
          mockPagerDutyAlert("Could not parse JSON in the getAccount response")
        }

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)

        result shouldBe Left("Could not parse getNsiAccount response, received 200 (OK), error=[Bonus terms list returned by NS&I was empty]")
      }

      "throw error when the getAccount response json missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
        val json = nsiAccountJson - "accountBalance"

        inSequence {
          mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))
          mockSendAuditEvent(event(json), nino)
          mockPagerDutyAlert("Could not parse JSON in the getAccount response")
        }

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)

        result.isLeft shouldBe true
      }

      "succeed when the NS&I response omits the emailAddress optional field" in {
        val json = nsiAccountJson - "emailAddress"
        mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))
        mockSendAuditEvent(event(json), nino)
        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)

        result shouldBe Right(Some(account.copy(accountHolderEmail = None)))
      }

      "handle non 200 responses from help-to-save-proxy" in {
        inSequence {
          mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(400, "")))
          mockPagerDutyAlert("Received unexpected http status in response to getAccount")
        }

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Left("Received unexpected status(400) from getNsiAccount call")
      }

      "handle unexpected server errors" in {
        inSequence {
          mockGet(getAccountUrl, queryParameters)(None)
          mockPagerDutyAlert("Failed to make call to getAccount")
        }

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
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

        mockGet(getAccountUrl, queryParameters)(Some(HttpResponse(400, errorResponse, returnHeaders)))

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Right(None)
      }

    }

    "retrieving transactions" must {
      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-transactions.version")

      val getTransactionsUrl: String = "http://localhost:7005/help-to-save-proxy/nsi-services/transactions"
      val queryParameters = Map(
        "nino" -> nino, "correlationId" → correlationId, "version" → version, "systemId" → systemId
      )

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

        mockGet(getTransactionsUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))

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

        inSequence {
          mockGet(getTransactionsUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))
          mockPagerDutyAlert("Could not parse get transactions response")
        }

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

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

        inSequence {
          mockGet(getTransactionsUrl, queryParameters)(Some(HttpResponse(200, json, returnHeaders)))
          mockPagerDutyAlert("Could not parse get transactions response")
        }

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Left("""Could not parse transactions response from NS&I, received 200 (OK), error=[Unknown value for operation: "bad"]""")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "handle non 200 responses from help-to-save-proxy" in {
        inSequence {
          mockGet(getTransactionsUrl, queryParameters)(Some(HttpResponse(400, "")))
          mockPagerDutyAlert("Received unexpected http status in response to get transactions")
        }

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
        result shouldBe Left("Received unexpected status(400) from get transactions call")
        timerMetricChange shouldBe 1
        errorMetricChange shouldBe 1
      }

      "handle unexpected server errors" in {
        inSequence {
          mockGet(getTransactionsUrl, queryParameters)(None)
          mockPagerDutyAlert("Failed to make call to get transactions")
        }

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
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

        mockGet(getTransactionsUrl, queryParameters)(Some(HttpResponse(400, errorResponse, returnHeaders)))

        val result = await(proxyConnector.getTransactions(nino, systemId, correlationId).value)
        result shouldBe Right(None)
      }

    }
  }
}
