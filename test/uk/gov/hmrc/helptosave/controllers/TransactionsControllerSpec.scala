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
import org.mockito.ArgumentMatchersSugar.*
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, _}
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, v2}
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.GGAndPrivilegedProviders
import uk.gov.hmrc.helptosave.models.account._

import java.time.LocalDate
import java.util.UUID

class TransactionsControllerSpec extends AuthSupport {

  val mockProxyConnector: HelpToSaveProxyConnector = mock[HelpToSaveProxyConnector]

  val controller = new TransactionsController(mockProxyConnector, mockAuthConnector, testCC)

  val transactions = Transactions(
    Seq(
      Transaction(
        operation = Credit,
        amount = BigDecimal("1.23"),
        transactionDate = LocalDate.parse("2018-06-08"),
        accountingDate = LocalDate.parse("2018-06-08"),
        description = "Debit card online deposit",
        transactionReference = "A1A11AA1A00A0034",
        balanceAfter = BigDecimal("1.23")
      )
    ))

  val queryString = s"nino=$nino&correlationId=${UUID.randomUUID()}&systemId=123"

  val fakeRequest = FakeRequest("GET", s"/nsi-account?$queryString")

  def mockGetTransactions(nino: String, systemId: String)(response: Either[String, Option[Transactions]]): Unit = {
      mockProxyConnector.getTransactions(nino, systemId, *)(*, *).returns(EitherT.fromEither(response))
  }

  "The TransactionsController" when {

    "retrieving getTransactions for an user" must {

      val systemId = "system"

      "handle success responses" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
            mockAuth()
            mockGetTransactions(nino, systemId)(Right(Some(transactions)))

          val result = controller.getTransactions(nino, systemId, None)(fakeRequest)
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(Some(transactions))
        }
      }

      "return a 403 (FORBIDDEN) if the NINO in the URL doesn't match the NINO retrieved from auth" in {
          mockAuth(GGAndPrivilegedProviders, v2.Retrievals.authProviderId)(Right(GGCredId("id")))
          mockAuth(EmptyPredicate, v2.Retrievals.nino)(Right(mockedNinoRetrieval))

        val result = controller.getTransactions("BE123456C", systemId, None)(fakeRequest)
        status(result) shouldBe 403
      }

      "return a 404 if an transactions does not exist for the NINO" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
            mockAuth()
            mockGetTransactions(nino, systemId)(Right(None))

          val result = controller.getTransactions(nino, systemId, None)(fakeRequest)
          status(result) shouldBe 404
        }
      }

      "return a 400 if the NINO is not valid" in {
        val result = controller.getTransactions("nino", systemId, None)(fakeRequest)
        status(result) shouldBe 400
      }

      "handle errors returned by the connector" in {
        testWithGGAndPrivilegedAccess { mockAuth =>
            mockAuth()
            mockGetTransactions(nino, systemId)(Left("some error"))

          val result = controller.getTransactions(nino, systemId, None)(fakeRequest)
          status(result) shouldBe 500
        }
      }
    }
  }
}
