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

import java.time.{LocalDate, YearMonth}
import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.function.MockFunction5
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, _}
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.AuthWithCL200
import uk.gov.hmrc.helptosave.models.account.{Account, Blocking}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccountControllerSpec extends AuthSupport {

  val mockProxyConnector = mock[HelpToSaveProxyConnector]

  val controller = new AccountController(mockProxyConnector, mockAuthConnector)

  val account = Account(YearMonth.of(1900, 1), "AC01", false, Blocking(false, false), 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), "Test Saver", Some("testsaver@example.com"), List(), None, None)

  val queryString = s"nino=$nino&correlationId=${UUID.randomUUID()}&systemId=123"

  val fakeRequest = FakeRequest("GET", s"/nsi-account?$queryString")

  def mockGetAccount(nino: String, systemId: String, correlationId: Option[String])(response: Either[String, Option[Account]]) = {
    val call: MockFunction5[String, String, String, HeaderCarrier, ExecutionContext, EitherT[Future, String, Option[Account]]] =
      mockProxyConnector.getAccount(_: String, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext)

    val callHandler = correlationId.fold(
      call.expects(nino, systemId, *, *, *)
    ){ id ⇒
        call.expects(nino, systemId, id, *, *)
      }

    callHandler.returning(EitherT.fromEither(response))
  }

  "The AccountController" when {

    "retrieving getAccount for an user" must {

      val systemId = "system"

      "handle success responses" in {
        inSequence {
          mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))
          mockGetAccount(nino, systemId, None)(Right(Some(account)))
        }

        val result = controller.getAccount(nino, systemId, None)(fakeRequest)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.toJson(Some(account))
      }

      "return a 403 (FORBIDDEN) if the NINO in the URL doesn't match the NINO retrieved from auth" in {
        mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))

        val result = controller.getAccount("BE123456C", systemId, None)(fakeRequest)
        status(result) shouldBe 403
      }

      "return a 404 if an account does not exist for the NINO" in {
        inSequence {
          mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))
          mockGetAccount(nino, systemId, None)(Right(None))
        }

        val result = controller.getAccount(nino, systemId, None)(fakeRequest)
        status(result) shouldBe 404
      }

      "return a 400 if the NINO is not valid" in {
        mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))

        val result = controller.getAccount("nino", systemId, None)(fakeRequest)
        status(result) shouldBe 400
      }

      "handle errors returned by the connector" in {
        inSequence {
          mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))
          mockGetAccount(nino, systemId, None)(Left("some error"))
        }

        val result = controller.getAccount(nino, systemId, None)(fakeRequest)
        status(result) shouldBe 500
      }
    }
  }
}
