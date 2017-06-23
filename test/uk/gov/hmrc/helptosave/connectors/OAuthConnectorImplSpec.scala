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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.Writes
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.WSHttp
import uk.gov.hmrc.helptosave.connectors.OAuthConnectorImpl.{Client, OAuthResponse, OAuthTokenConfiguration}
import uk.gov.hmrc.helptosave.models.OAuthTokens
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class OAuthConnectorImplSpec extends WordSpec with Matchers with MockFactory with GeneratorDrivenPropertyChecks {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val oauthConfig = OAuthTokenConfiguration("url", Client("id", "secret"), "callback")

  val mockHttp = mock[WSHttp]

  val url = "url"
  val clientID = "id"
  val clientSecret = "secret"
  val callbackURL = "callback"

  val connector = new OAuthConnectorImpl(Configuration(ConfigFactory.parseString(
    s"""
       |oauth {
       |  url = $url
       |  client {
       |    id = $clientID
       |    secret = $clientSecret
       |  }
       |  callbackURL = $callbackURL
       |}
      """.stripMargin))) {
    override val http = mockHttp
  }

  type PostBody = Map[String,Seq[String]]

  def mockGetToken(authorisationCode: String)(response: HttpResponse): Unit =
    (mockHttp.postForm(_: String, _: Map[String,Seq[String]])(_: HeaderCarrier))
      .expects(
        url,
        Map(
          "redirect_uri" → Seq(callbackURL),
          "grant_type" -> Seq("authorization_code"),
          "code" → Seq(authorisationCode),
          "client_id" → Seq(clientID),
          "client_secret" → Seq(clientSecret)),
        *
      )
      .returning(Future.successful(response))

  def mockRefreshToken(refreshToken: String)(response: HttpResponse): Unit =
    (mockHttp.postForm(_: String, _: Map[String,Seq[String]])(_: HeaderCarrier))
      .expects(
        url,
        Map(
          "grant_type" -> Seq("refresh_token"),
          "refresh_token" → Seq(refreshToken),
          "client_id" → Seq(clientID),
          "client_secret" → Seq(clientSecret)),
        *
      )
      .returning(Future.successful(response))

  def await[A](f: Future[A]) = Await.result(f, 5.seconds)

  case class Test(description: String,
                  mock: (String, HttpResponse) ⇒ Unit,
                  action: String ⇒ Result[OAuthTokens])

  val getTokenTest = Test(
    "getting",
    {(s: String, r: HttpResponse) ⇒ mockGetToken(s)(r) },
    { s: String ⇒ connector.getToken(s) }
  )

  val refreshTokenTest = Test(
    "refreshing",
    {(s: String, r: HttpResponse) ⇒ mockRefreshToken(s)(r) },
    { s: String ⇒ connector.refreshToken(s) }
  )

  "The OAuthConnectorImpl" when {

    List(getTokenTest, refreshTokenTest).foreach{ case Test(description, mock, action) ⇒

      s"$description a token" must {

        "return the tokens if the call is successful" in {
          val json = Json.toJson(OAuthResponse("access", "refresh", 0L))
          mock("hello", HttpResponse(Status.OK, Some(json)))

          val result = action("hello")
          await(result.value) shouldBe Right(OAuthTokens("access", "refresh"))
        }

        "return an error" when {

          def testFailure(result: Result[OAuthTokens]): Unit =
            await(result.value).isLeft shouldBe true

          "the body of the response is not in JSON format" in {
            mock("hello", HttpResponse(Status.OK, responseString = Some("not JSON")))

            testFailure(action("hello"))
          }

          "the JSON in the response is of the wrong format" in {
            val json = Json.parse("""{ "x" : 1 }""")
            mock("hello", HttpResponse(Status.OK, Some(json)))

            testFailure(action("hello"))
          }

          "the HTTP status of the response is not 200" in {
            forAll { (status: Int) ⇒
              whenever(status != Status.OK){
                // we wouldn't actually get response JSON if we don't get a 200 -
                // pass the response JSON in this test to verify that a non-200
                // status returns an error regardless of the repsonse body
                val json = Json.toJson(OAuthResponse("access", "refresh", 0L))
                mock("hello", HttpResponse(Status.BAD_REQUEST, Some(json)))

                testFailure(action("hello"))
              }
            }
          }
        }
      }
    }
  }
}
