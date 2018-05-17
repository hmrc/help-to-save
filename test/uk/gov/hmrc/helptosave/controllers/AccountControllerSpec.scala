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

import java.time.LocalDate
import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, _}
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.AuthWithCL200
import uk.gov.hmrc.helptosave.models.account.{Account, AccountO, Blocking}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class AccountControllerSpec extends AuthSupport {

  val mockProxyConnector = mock[HelpToSaveProxyConnector]

  val controller = new AccountController(mockProxyConnector, mockAuthConnector)

  val account = Account(false, Blocking(false), 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

  val queryString = s"nino=$nino&correlationId=${UUID.randomUUID()}&systemId=123"

  val fakeRequest = FakeRequest("GET", s"/nsi-account?$queryString")

  def mockGetAccount(nino: String, queryString: String)(response: Either[String, AccountO]) = {
    (mockProxyConnector.getAccount(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, queryString, *, *)
      .returning(EitherT.fromEither(response))
  }

  "The AccountController" when {

    "retrieving getAccount for an user" must {

      "handle success responses" in {
        mockAuthResultWithSuccess(AuthWithCL200)(mockedNinoRetrieval)
        mockGetAccount(nino, queryString)(Right(AccountO(Some(account))))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.toJson(AccountO(Some(account)))
      }

      "handle errors returned by the connector" in {
        mockAuthResultWithSuccess(AuthWithCL200)(mockedNinoRetrieval)
        mockGetAccount(nino, queryString)(Left("some error"))

        val result = controller.getAccount()(fakeRequest)
        status(result) shouldBe 500
      }
    }
  }
}
