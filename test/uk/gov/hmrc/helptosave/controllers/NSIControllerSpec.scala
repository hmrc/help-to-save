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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosave.connectors.NSIConnector
import uk.gov.hmrc.helptosave.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


class NSIControllerSpec extends WordSpec with Matchers with MockFactory {

  case class UnrelatedJson(a: Int, b: String)

  implicit val reads: Writes[UnrelatedJson] = Json.writes[UnrelatedJson]

  val connector = mock[NSIConnector]


  def mockConnector(userInfo: NSIUserInfo)(result: Either[SubmissionFailure, SubmissionSuccess]) =
    (connector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(userInfo, *, *)
      .returning(Future.successful(result))

  val controller = new NSIController(connector)

  "The NSIController" when {

    "handling requests to create an account" must {

      "return with a BadRequest" when {

        "the request body does not contain JSON" in {
          val request = FakeRequest()
          val result = controller.createAccount()(request)
          status(result) shouldBe 400
        }

        "the request body contains the wrong JSON" in {
          val request = FakeRequest().withJsonBody(Json.toJson(UnrelatedJson(1, "?")))
          val result = controller.createAccount()(request)
          status(result) shouldBe 400
        }

        "the user info in the request body does not pass NSI validation checks" in {
          val request = FakeRequest().withJsonBody(Json.toJson(randomUserDetails()))
          val result = controller.createAccount()(request)
          status(result) shouldBe 400
        }

      }

      "return with an InternalServerError" when {

        "the call NSI does not return with a 201 (Created) status" in {
          mockConnector(validNSIUserInfo)(Left(SubmissionFailure("Oh no!!")))

          val request = FakeRequest().withJsonBody(Json.toJson(validUserInfo))
          val result = controller.createAccount()(request)
          status(result) shouldBe 500
        }

      }

      "return with a Created" when {
        "the user info passes the NSI validation checks and the call to NSI comes back successfully" in {
          mockConnector(validNSIUserInfo)(Right(SubmissionSuccess()))

          val request = FakeRequest().withJsonBody(Json.toJson(validUserInfo))
          val result = controller.createAccount()(request)
          status(result) shouldBe 201
        }
      }
    }
  }
}
