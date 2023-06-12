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

import java.util.Base64

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authProviderId => v2AuthProviderId, nino => v2Nino}
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, PAClientId}
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth._
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.util.NINO

import scala.concurrent.{ExecutionContext, Future}

class EmailStoreControllerSpec extends AuthSupport {

  val emailStore: EmailStore = mock[EmailStore]

  def mockStore(email: String, nino: NINO)(result: Either[String, Unit]): Unit =
    (emailStore.store(_: String, _: NINO)(_: ExecutionContext))
      .expects(email, nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGet(nino: NINO)(result: Either[String, Option[String]]): Unit =
    (emailStore.get(_: NINO)(_: ExecutionContext))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))

  "The EmailStoreController" when {

    val controller = new EmailStoreController(emailStore, mockAuthConnector, testCC)
    val email = "email"
    val encodedEmail = new String(Base64.getEncoder.encode(email.getBytes()))

      def store(email: String, nino: Option[String]): Future[Result] =
        controller.store(email, nino)(FakeRequest())

    "handling requests to store emails" must {

      "return a HTTP 200 if the email is successfully stored with a GG login" in {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(GGCredId("")))
          mockAuth(EmptyPredicate, v2Nino)(Right(Some(nino)))
          mockStore(email, nino)(Right(()))
        }

        status(store(encodedEmail, None)) shouldBe 200
      }

      "return a HTTP 200 if the email is successfully stored with a privileged login" in {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(PAClientId("")))
          mockStore(email, nino)(Right(()))
        }

        status(store(encodedEmail, Some(nino))) shouldBe 200
      }

      "return a HTTP 500" when {

        "the email cannot be decoded" in {
          mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(PAClientId("")))

          status(store("not base 64 encoded", Some(nino))) shouldBe 500
        }

        "the email is not successfully stored" in {
          inSequence {
            mockAuth(GGAndPrivilegedProviders, v2AuthProviderId)(Right(PAClientId("")))
            mockStore(email, nino)(Left(""))
          }

          status(store(encodedEmail, Some(nino))) shouldBe 500
        }
      }

    }

    "handling requests to get emails" must {

      val email = "email"

        def get(): Future[Result] = controller.get()(FakeRequest())

      "get the email from the email store" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGet(nino)(Right(None))
        await(get())
      }

      "return an OK with the email if the email exists" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGet(nino)(Right(Some(email)))

        val result = get()
        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.parse(
          s"""
             |{
             |  "email" : "$email"
             |}
          """.stripMargin
        )
      }

      "return an OK with empty JSON if the email does not exist" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGet(nino)(Right(None))

        val result = get()
        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.parse("{}")
      }

      "return a HTTP 500 if there is an error getting from the email store" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockGet(nino)(Left("oh no"))

        val result = get()
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

  }

}
