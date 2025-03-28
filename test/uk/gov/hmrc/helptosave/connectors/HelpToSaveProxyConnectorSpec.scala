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

package uk.gov.hmrc.helptosave.connectors

import org.mockito.ArgumentMatchers.*
import org.scalatest.EitherValues
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.mvc.Http.Status._
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.models.account._
import uk.gov.hmrc.helptosave.util.WireMockMethods
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestEnrolmentBehaviour}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.WireMockSupport

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

// scalastyle:off magic.number
class HelpToSaveProxyConnectorSpec
    extends TestEnrolmentBehaviour with MockPagerDuty with EitherValues with WireMockSupport with WireMockMethods {

  val mockAuditor: HTSAuditor = mock[HTSAuditor]

  override lazy val additionalConfig: Configuration = {
    Configuration(
      "microservice.services.help-to-save-proxy.host" -> wireMockHost,
      "microservice.services.help-to-save-proxy.port" -> wireMockPort
    )
  }

  val mockHttp: HttpClientV2 = fakeApplication.injector.instanceOf[HttpClientV2]
  override val proxyConnector =
    new HelpToSaveProxyConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, mockAuditor, servicesConfig)

  val createAccountURL: String = "/help-to-save-proxy/create-account"
  val updateEmailURL: String = "/help-to-save-proxy/update-email"

  val connectorCallFailureMessage: (HTTPMethod, String) => String = (httpMethod, urlPath) => {
    s"$httpMethod of 'http://$wireMockHost:$wireMockPort$urlPath' failed. Caused by: 'Connection refused"
  }

  val userInfo: NSIPayload =
    NSIPayload(
      "forename",
      "surname",
      LocalDate.now(),
      "nino",
      ContactDetails(
        "address1",
        "address2",
        Some("address3"),
        Some("address4"),
        Some("address5"),
        "postcode",
        Some("GB"),
        Some("phoneNumber"),
        Some("email"),
        "commPref"),
      "regChannel",
      None,
      Some("version"),
      Some("systemId")
    )

  def mockSendAuditEvent(event: GetAccountResultEvent, nino: String): Unit = 
    mockAuditor
      .sendEvent(event, nino)(any)

  def transactionMetricChanges[T](body: => T): (T, Long, Long) = {
    val timerCountBefore = mockMetrics.getTransactionsTimer.getCount
    val errorCountBefore = mockMetrics.getTransactionsErrorCounter.getCount
    val result = body
    (
      result,
      mockMetrics.getTransactionsTimer.getCount - timerCountBefore,
      mockMetrics.getTransactionsErrorCounter.getCount - errorCountBefore)
  }

  "The HelpToSaveProxyConnector" when {
    "creating account" must {

      "handle 201 response from the help-to-save-proxy" in {
        when(POST, createAccountURL, body = Some(Json.toJson(userInfo).toString())).thenReturn(Status.CREATED)

        val result = await(proxyConnector.createAccount(userInfo))
        result.value.status shouldBe CREATED
      }

      "handle 409 response from the help-to-save-proxy" in {
        when(POST, createAccountURL, body = Some(Json.toJson(userInfo).toString())).thenReturn(Status.CONFLICT)

        val result = await(proxyConnector.createAccount(userInfo))
        result.leftSide shouldBe Left(UpstreamErrorResponse("Upstream Error",CONFLICT))
      }

      "handle bad_request response from frontend" in {
        when(POST, createAccountURL, body = Some(Json.toJson(userInfo).toString())).thenReturn(Status.BAD_REQUEST)

        val result = await(proxyConnector.createAccount(userInfo))
        result.leftSide shouldBe Left(UpstreamErrorResponse("Upstream Error",BAD_REQUEST))
      }
    }

    "update email" must {

      "handle 200 response from the help-to-save-proxy" in {
        when(PUT, updateEmailURL, body = Some(Json.toJson(userInfo).toString())).thenReturn(Status.OK)

        val result = await(proxyConnector.updateEmail(userInfo))
        result.value.status shouldBe OK
      }
    }

    "querying DWP for UC Claimant checks" must {

      val txnId = UUID.randomUUID()
      val nino = "AE123456C"
      val threshold = 650.0

      val url = "/help-to-save-proxy/uc-claimant-check"
      val queryParams = Map("nino" -> nino, "transactionId" -> txnId.toString, "threshold" -> threshold.toString)

      "handle success response from proxy" in {

        def test(uCResponse: UCResponse): Unit =
          withClue(s"For UCResponse $uCResponse:") {
            when(GET, url, queryParams).thenReturn(Status.OK, Json.toJson(uCResponse).toString())

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)
            result shouldBe Right(uCResponse)
          }

        test(UCResponse(ucClaimant = true, Some(true)))
        test(UCResponse(ucClaimant = true, Some(false)))
        test(UCResponse(ucClaimant = false, None))

      }

      "handle bad_request response from frontend" in {
        when(GET, url, queryParams).thenReturn(Status.BAD_REQUEST)

        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)
        result shouldBe Left("Received unexpected status(400) from UniversalCredit check")
      }

      "handles failures due to invalid json" in {

        def test(json: String) =
          withClue(s"For json $json:") {
            when(GET, url, queryParams).thenReturn(Status.OK, Json.toJson(json).toString())

            val result = Await.result(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value, 5.seconds)
            result.left.value contains "unable to parse UCResponse from proxy"
          }

        test("""{"foo": "bar"}""")
        test("""{"ucClaimant":"foo", "withinThreshold":"bar"}""")
      }
    }

    "retrieving NsiAccount" must {

      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-account.version")

      val getAccountUrl: String = "/help-to-save-proxy/nsi-services/account"
      val queryParameters =
        Map("nino" -> nino, "correlationId" -> correlationId, "version" -> version, "systemId" -> systemId)

      val path = s"/help-to-save/$nino/account?nino=$nino&systemId=$systemId&correlationId=$correlationId"
      def event(accountJson: JsValue = nsiAccountJson) =
        GetAccountResultEvent(GetAccountResult(nino, accountJson), path)

      "handle success response with Accounts having Terms" in {
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.OK, Json.toJson(nsiAccountJson).toString())
        mockSendAuditEvent(event(), nino)

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Right(Some(account))
      }

      "throw error when there are no Terms in the json" in {
        val json = nsiAccountJson + ("terms" -> Json.arr())
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.OK, json.toString())

        mockSendAuditEvent(event(json), nino)
        mockPagerDutyAlert("Could not parse JSON in the getAccount response")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Left(
          "Could not parse getNsiAccount response, received 200 (OK), error=[Bonus terms list returned by NS&I was empty]")
      }

      "throw error when the getAccount response json missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
        val json = nsiAccountJson - "accountBalance"
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.OK, json.toString())
        mockSendAuditEvent(event(json), nino)
        mockPagerDutyAlert("Could not parse JSON in the getAccount response")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result.isLeft shouldBe true
      }

      "succeed when the NS&I response omits the emailAddress optional field" in {
        val json = nsiAccountJson - "emailAddress"
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.OK, json.toString())
        mockSendAuditEvent(event(json), nino)

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Right(Some(account.copy(accountHolderEmail = None)))
      }

      "handle non 200 responses from help-to-save-proxy" in {
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.BAD_REQUEST)
        mockPagerDutyAlert("Received unexpected http status in response to getAccount")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Left("Received unexpected status(400) from getNsiAccount call")
      }

      "handle unexpected server errors" in {
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.INTERNAL_SERVER_ERROR)
        mockPagerDutyAlert("Failed to make call to getAccount")

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result.isLeft shouldBe true
      }

      "handle the case where an account does not exist" in {
        val errorResponse =
          Json.parse(s"""
                        |{
                        |  "errors" : [
                        |    {
                        |      "errorMessageId" : "id",
                        |      "errorMessage"   : "message",
                        |      "errorDetail"    : "detail"
                        |    },
                        |    {
                        |      "errorMessageId" : "${appConfig.runModeConfiguration.underlying.getString(
                          "nsi.no-account-error-message-id")}",
                        |      "errorMessage"   : "Oh no!",
                        |      "errorDetail"    : "Account doesn't exist"
                        |    }
                        |  ]
                        |}
          """.stripMargin)
        when(GET, getAccountUrl, queryParameters).thenReturn(Status.BAD_REQUEST, errorResponse.toString())

        val result = await(proxyConnector.getAccount(nino, systemId, correlationId, path).value)
        result shouldBe Right(None)
      }

    }

    "retrieving transactions" must {
      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-transactions.version")

      val getTransactionsUrl: String = "/help-to-save-proxy/nsi-services/transactions"
      val queryParameters = Map(
        "nino"          -> nino,
        "correlationId" -> correlationId,
        "version"       -> version,
        "systemId"      -> systemId
      )

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
        when(GET, getTransactionsUrl, queryParameters).thenReturn(Status.OK, json.toString())

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Right(
          Some(Transactions(Seq(
            Transaction(
              Credit,
              BigDecimal("11.50"),
              LocalDate.parse("2017-11-20"),
              LocalDate.parse("2017-11-20"),
              "Debit card online deposit",
              "A1A11AA1A00A0034",
              BigDecimal("11.50")
            ),
            Transaction(
              Debit,
              BigDecimal("1.01"),
              LocalDate.parse("2017-11-27"),
              LocalDate.parse("2017-11-27"),
              "BACS payment",
              "A1A11AA1A00A000I",
              BigDecimal("10.49")),
            Transaction(
              Debit,
              BigDecimal("1.11"),
              LocalDate.parse("2017-11-27"),
              LocalDate.parse("2017-11-27"),
              "BACS payment",
              "A1A11AA1A00A000G",
              BigDecimal("9.38")),
            Transaction(
              Credit,
              BigDecimal("1.11"),
              LocalDate.parse("2017-11-27"),
              LocalDate.parse("2017-12-04"),
              "Reinstatement Adjustment",
              "A1A11AA1A00A000G",
              BigDecimal("10.49")
            ),
            Transaction(
              Credit,
              BigDecimal(50),
              LocalDate.parse("2018-04-10"),
              LocalDate.parse("2018-04-10"),
              "Debit card online deposit",
              "A1A11AA1A00A0059",
              BigDecimal("60.49"))
          ))))
        timerMetricChange shouldBe 0
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
        when(GET, getTransactionsUrl, queryParameters).thenReturn(Status.OK, json.toString())
        mockPagerDutyAlert("Could not parse get transactions response")

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Left(
          "Could not parse transactions response from NS&I, received 200 (OK), error=[Could not parse http response JSON: /transactions(0)/sequence: [error.path.missing]]")
        timerMetricChange shouldBe 0
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
        when(GET, getTransactionsUrl, queryParameters).thenReturn(Status.OK, json.toString())
        mockPagerDutyAlert("Could not parse get transactions response")

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))

        result shouldBe Left(
          """Could not parse transactions response from NS&I, received 200 (OK), error=[Unknown value for operation: "bad"]""")
        timerMetricChange shouldBe 0
        errorMetricChange shouldBe 1
      }

      "handle non 200 responses from help-to-save-proxy" in {
        when(GET, getTransactionsUrl, queryParameters).thenReturn(Status.BAD_REQUEST, "")
        mockPagerDutyAlert("Received unexpected http status in response to get transactions")

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
        result shouldBe Left("Received unexpected status(400) from get transactions call")
        timerMetricChange shouldBe 0
        errorMetricChange shouldBe 1
      }

      "handle the case where an account does not exist by returning Right(None)" in {
        val errorResponse =
          Json.parse(s"""
                        |{
                        |  "errors" : [
                        |    {
                        |      "errorMessageId" : "id",
                        |      "errorMessage"   : "message",
                        |      "errorDetail"    : "detail"
                        |    },
                        |    {
                        |      "errorMessageId" : "${appConfig.runModeConfiguration.underlying.getString(
                          "nsi.no-account-error-message-id")}",
                        |      "errorMessage"   : "Oh no!",
                        |      "errorDetail"    : "Account doesn't exist"
                        |    }
                        |  ]
                        |}
          """.stripMargin)
        when(GET, getTransactionsUrl, queryParameters).thenReturn(Status.BAD_REQUEST, errorResponse)

        val result = await(proxyConnector.getTransactions(nino, systemId, correlationId).value)
        result shouldBe  Right(None)
      }

    }
  }

  "Failed calls to HelpToSaveConnector" when {
    "querying DWP for UC Claimant checks" must {

      val txnId = UUID.randomUUID()
      val nino = "AE123456C"
      val threshold = 650.0

      val url = "/help-to-save-proxy/uc-claimant-check"
      val queryParams = Map("nino" -> nino, "transactionId" -> txnId.toString, "threshold" -> threshold.toString)

      "handle unexpected errors" in {
        val expectedStatusCode = 500
        when(GET, url, queryParams).thenReturn(expectedStatusCode)

        val result = await(proxyConnector.ucClaimantCheck(nino, txnId, threshold).value)
        result shouldBe Left(s"Received unexpected status($expectedStatusCode) from UniversalCredit check")
      }
    }

    "creating account" must {
      "handle unexpected errors" in {
        wireMockServer.stop()
        when(POST, createAccountURL, body = Some(Json.toJson(userInfo).toString()))

        val result = await(proxyConnector.createAccount(userInfo))
        val jsonResult = result.leftSideValue
        jsonResult contains Left(UpstreamErrorResponse(s"unexpected error from proxy during /create-de-account ${connectorCallFailureMessage(POST, s"$createAccountURL")}",INTERNAL_SERVER_ERROR))

        wireMockServer.start()
      }
    }

    "retrieving transactions" must {
      val nino = randomNINO()
      val correlationId = UUID.randomUUID().toString
      val systemId = "123"
      val version = appConfig.runModeConfiguration.underlying.getString("nsi.get-transactions.version")

      val queryParameters = Map(
        "nino" -> nino,
        "correlationId" -> correlationId,
        "version" -> version,
        "systemId" -> systemId
      )
      val getTransactionsUrl: String = "/help-to-save-proxy/nsi-services/transactions"

      "handle unexpected server errors" in {
        wireMockServer.stop()
        when(GET, getTransactionsUrl, queryParameters)
        mockPagerDutyAlert("Failed to make call to get transactions")

        val (result, timerMetricChange, errorMetricChange) =
          transactionMetricChanges(await(proxyConnector.getTransactions(nino, systemId, correlationId).value))
        result.isLeft shouldBe true
        result.left.value should include(
          s"Call to get transactions unsuccessful: ${connectorCallFailureMessage(GET, getTransactionsUrl)}"
        )
        timerMetricChange shouldBe 0
        errorMetricChange shouldBe 1
        wireMockServer.start()
      }
    }
  }
}
