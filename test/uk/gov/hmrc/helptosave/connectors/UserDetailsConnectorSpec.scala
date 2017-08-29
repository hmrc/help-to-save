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

package uk.gov.hmrc.helptosave.connectors

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class UserDetailsConnectorSpec extends TestSupport with WithFakeApplication {

  def mockGet(userDetailsUri: String)(response: HttpResponse) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(userDetailsUri, Map.empty[String, String], *, *)
      .returning(Future.successful(response))

  lazy val connector = new UserDetailsConnectorImpl {
    override val http = mockHttp
  }

  def getUserDetails(userDetailsUri: String): Either[String, UserDetailsResponse] =
    Await.result(connector.getUserDetails(userDetailsUri).value, 5.seconds)

  "getUserDetails" must {

    lazy val userDetailsUri = "url"

    "return user details when there are user details to return" in {
      val expected = UserDetailsResponse("name", Some("lastname"), Some("email"), Some(randomDate()))
      mockGet(userDetailsUri)(HttpResponse(200, Some(Json.toJson(expected)))) // scalastyle:ignore magic.number

      getUserDetails(userDetailsUri) shouldBe Right(expected)
    }

    "return an error" when {

        def testFailure(mockActions: ⇒ Unit): Unit = {
          mockActions
          getUserDetails(userDetailsUri).isLeft shouldBe true
        }

      "there is an error calling the API" in {
        val error = new Exception("Oh no!")
        testFailure(
          (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
            .expects(userDetailsUri, Map.empty[String, String], *, *)
            .returning(Future.failed(error))
        )
      }

      "the API doesn't return JSON in the response body" in {
        testFailure(
          mockGet(userDetailsUri)(HttpResponse(200, responseString = Some("hello"))) //scalastyle:ignore magic.number
        )
      }
    }
  }
}
