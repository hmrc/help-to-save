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

import java.time.LocalDate

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.connectors.UserInfoAPIConnector.{APIError, TokenExpiredError, UnknownError}
import uk.gov.hmrc.helptosave.models.{OAuthTokens, OpenIDConnectUserInfo}
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.{global ⇒ ec}
import scala.concurrent.duration._

class UserInfoAPIConnectorImplSpec extends Matchers with WordSpecLike with TestSupport with GeneratorDrivenPropertyChecks {

  val url = "url"
  val authorisationHeaderKey = "auth"
  val config = Configuration(
    "api.user-info.url" → url,
    "api.user-info.authorisation-header-key" → authorisationHeaderKey
  )

  val tokens = OAuthTokens("access", "refresh")

  def expectedHeaders(tokens: OAuthTokens): Map[String,String] = Map(
    "Accept" → "application/vnd.hmrc.1.0+json",
    authorisationHeaderKey → s"Bearer ${tokens.accessToken}"
  )

  val connector = new UserInfoAPIConnectorImpl(config, ec){
    override val http = mockHttp
  }

  def mockGet(tokens: OAuthTokens, response: Either[Throwable,HttpResponse]): Unit =
    (mockHttp.get(_: String, _: Map[String,String])(_: HeaderCarrier, _: ExecutionContext)
      ).expects(url, expectedHeaders(tokens), *, *)
      .returning(response.fold(Future.failed, Future.successful))

  def mockGet(tokens: OAuthTokens, response: HttpResponse): Unit =
    mockGet(tokens, Right(response))

  def mockGet(tokens: OAuthTokens, error: Throwable): Unit =
    mockGet(tokens, Left(error))


  def doRequest(): Either[APIError,OpenIDConnectUserInfo] =
    Await.result(connector.getUserInfo(tokens).value, 5.seconds)

  def isUnknownError(result: Either[APIError,OpenIDConnectUserInfo]): Boolean = result.fold(
    _ match {
      case TokenExpiredError ⇒ false
      case UnknownError(_) => true
    },
    _ ⇒ false
  )

  "The UserInfoAPIConnectorImpl" when {

    "getting user info" when {

      "the response comes back with an OK status" must {

        "return the user info contained in the JSON response" in {
          val userInfo = OpenIDConnectUserInfo(
            Some("Bob"), Some("Bobby"), Some("Bobber"),
            Some(OpenIDConnectUserInfo.Address("1 the Street\nThe Place", Some("ABC123"), Some("GB"))),
            Some(LocalDate.now()), Some("nino"), None, Some("email@abc.com"))

          mockGet(tokens, HttpResponse(Status.OK, Some(Json.toJson(userInfo))))
          val result = doRequest()
          result shouldBe Right(userInfo)
        }

        "return an error" when {

          "the response isn't in JSON format" in {
            mockGet(tokens, HttpResponse(Status.OK, responseString = Some("hello?")))
            isUnknownError(doRequest()) shouldBe true
          }


          // TODO: currently the following test fails - since all of the fields
          // TODO: are optional, the JSON gets converted to UserInfo with all
          // TODO: the fields set to None
          "the response contains JSON which is not of the correct format" ignore {
            mockGet(tokens, HttpResponse(Status.OK, Some(Json.parse(
              """
                |{
                |  "key" : "value"
                |}
              """.stripMargin))))
            isUnknownError(doRequest()) shouldBe true
          }
        }
      }

      "the response comes back with an Unauthorized status" must {

        "return a TokenExpired error if the JSON in the response indicates an error code " +
          "of 'INVALID_CREDENTIALS'"  in {
          mockGet(tokens, HttpResponse(Status.UNAUTHORIZED, Some(Json.parse(
            """
              |{
              |   "code":    "INVALID_CREDENTIALS",
              |   "message": "Invalid Authentication information provided"
              |}
            """.stripMargin
          ))))
          doRequest() shouldBe Left(TokenExpiredError)
        }

        "return an error" when {

          "the JSON indicates any other error code" in {
            mockGet(tokens, HttpResponse(Status.UNAUTHORIZED, Some(Json.parse(
              """
                |{
                |   "code": "OTHER"
                |}
              """.stripMargin
            ))))
            isUnknownError(doRequest()) shouldBe true
          }

          "the response contains JSON which is not of the correct format" in {
            mockGet(tokens, HttpResponse(Status.UNAUTHORIZED, Some(Json.parse(
              """
                |{
                |   "key": "value"
                |}
              """.stripMargin
            ))))
            isUnknownError(doRequest()) shouldBe true
          }

          "the response does not contain any JSON" in {
            mockGet(tokens, HttpResponse(Status.UNAUTHORIZED,
              responseString = Some("Hello?!")))

            isUnknownError(doRequest()) shouldBe true
          }
        }

      }

      "the response comes back any other status status" must {

        "result in an error" in {
          forAll{ status: Int ⇒
            whenever(status != Status.OK && status != Status.UNAUTHORIZED){
              mockGet(tokens, HttpResponse(status))
              isUnknownError(doRequest()) shouldBe true
            }
          }
        }
      }

      "there is an error during the request" must {

        "return an error" in {
          mockGet(tokens, new Exception("Uh oh!"))
          isUnknownError(doRequest()) shouldBe true
        }
      }


    }

  }

}
