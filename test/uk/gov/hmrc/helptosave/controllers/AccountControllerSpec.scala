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
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, *}
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.credentials
import uk.gov.hmrc.auth.core.retrieve.v2
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.GGAndPrivilegedProviders
import uk.gov.hmrc.helptosave.models.account.{Account, Blocking}

import java.time.{LocalDate, YearMonth}
import java.util.UUID

class AccountControllerSpec extends AuthSupport {

  val mockProxyConnector: HelpToSaveProxyConnector = mock[HelpToSaveProxyConnector]

  val controller = new AccountController(mockProxyConnector, mockAuthConnector, testCC)

  val account: Account = Account(
    YearMonth.of(1900, 1),
    "AC01",
    isClosed = false,
    Blocking(payments = false, withdrawals = false, bonuses = false),
    123.45,
    0,
    0,
    0,
    LocalDate.parse("1900-01-01"),
    "Test",
    "Saver",
    Some("testsaver@example.com"),
    List(),
    None,
    None
  )

  val queryString: String = s"nino=$nino&correlationId=${UUID.randomUUID()}&systemId=123"

  val path: String = s"/help-to-save/$nino/account?$queryString"

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", path)

  def mockGetAccount(nino: String, systemId: String, path: String)(response: Either[String, Option[Account]]): Any =
    when(mockProxyConnector.getAccount(eqTo(nino), eqTo(systemId), any(), eqTo(path))(any(), any()))
      .thenReturn(EitherT.fromEither(response))

  "The AccountController" when {

    "retrieving getAccount for an user" must {

      val systemId = "system"

      "handle success responses" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
          mockAuth()
          mockGetAccount(nino, systemId, path)(Right(Some(account)))

          val result = controller.getAccount(nino, systemId, None)(fakeRequest)
          status(result)        shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(Some(account))
        }
      }

      "return a 403 (FORBIDDEN) if the NINO in the URL doesn't match the NINO retrieved from auth" in {
        mockAuth(GGAndPrivilegedProviders, credentials)(Right(ggCredentials))
        mockAuth(EmptyPredicate, v2.Retrievals.nino)(Right(mockedNinoRetrieval))

        val result = controller.getAccount("BE123456C", systemId, None)(fakeRequest)
        status(result) shouldBe 403
      }

      "return a 404 if an account does not exist for the NINO" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
          mockAuth()
          mockGetAccount(nino, systemId, path)(Right(None))

          val result = controller.getAccount(nino, systemId, None)(fakeRequest)
          status(result) shouldBe 404
        }
      }

      "return a 400 if the NINO is not valid" in {
        val result = controller.getAccount("nino", systemId, None)(fakeRequest)
        status(result) shouldBe 400
      }

      "handle errors returned by the connector" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
          mockAuth()
          mockGetAccount(nino, systemId, path)(Left("some error"))

          val result = controller.getAccount(nino, systemId, None)(fakeRequest)
          status(result) shouldBe 500
        }
      }
    }
  }
}
