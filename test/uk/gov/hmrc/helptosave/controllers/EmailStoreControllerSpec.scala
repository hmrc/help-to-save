/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.{ExecutionContext, Future}

class EmailStoreControllerSpec extends TestSupport {

  val emailStore: EmailStore = mock[EmailStore]

  def mockStore(email: String, nino: NINO)(result: Either[String,Unit]): Unit =
    (emailStore.storeConfirmedEmail(_: String,_ : NINO)(_: ExecutionContext))
    .expects(email, nino, *)
    .returning(EitherT.fromEither[Future](result))

  "The EmailStoreController" when {

    val controller = new EmailStoreController(emailStore)
    val email = "email"
    val encodedEmail = new String(Base64.getEncoder.encode(email.getBytes()))
    val nino = "NINO"

    def store(email: String): Future[Result] =
      controller.store(email, nino)(FakeRequest())

    "handling requests to store emails" must {

      "decode the email and store it with the email store" in {
        mockStore(email, nino)(Left(""))
        store(encodedEmail)
      }

      "return a HTTP 200 if the email is successfully stored" in {
        mockStore(email, nino)(Right(()))
        status(store(encodedEmail)) shouldBe 200
      }

      "return a HTTP 500" when {

        "the email cannot be decoded" in {
          status(store("not base 64 encoded")) shouldBe 500
        }

        "the email is not successfully stored" in {
          mockStore(email, nino)(Left(""))
          status(store(encodedEmail)) shouldBe 500
        }
      }

    }
  }

}
