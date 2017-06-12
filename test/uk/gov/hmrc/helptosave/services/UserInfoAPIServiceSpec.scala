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

package uk.gov.hmrc.helptosave.services

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.helptosave.connectors.{OAuthConnector, UserInfoAPIConnector}
import uk.gov.hmrc.helptosave.connectors.UserInfoAPIConnector._
import uk.gov.hmrc.helptosave.models.userinfoapi.{OAuthTokens, UserInfo}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class UserInfoAPIServiceSpec extends WordSpec with Matchers with MockFactory {


  class TestApparatus {
    implicit val hc = HeaderCarrier()

    val nino = "NINO"

    val userInfoAPIConnector = mock[UserInfoAPIConnector]

    val oauthConnector = mock[OAuthConnector]

    val service = new UserInfoAPIService(userInfoAPIConnector, oauthConnector)

    def mockGetUserInfo(tokens: OAuthTokens)(response: Either[APIError, UserInfo]): Unit =
      (userInfoAPIConnector.getUserInfo(_: OAuthTokens)(_: HeaderCarrier, _: ExecutionContext))
        .expects(tokens, *, *)
        .returning(EitherT(Future.successful(response)))

    def mockGetTokens(authorisationCode: String)(response: Either[String, OAuthTokens]): Unit =
      (oauthConnector.getToken(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authorisationCode, *, *)
        .returning(EitherT(Future.successful(response)))

    def mockRefreshTokens(tokens: OAuthTokens)(response: Either[String, OAuthTokens]): Unit =
      (oauthConnector.refreshToken(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(tokens.refreshToken, *, *)
        .returning(EitherT(Future.successful(response)))

    def getUserInfo(authorisationCode: String, waitTime: FiniteDuration = 5.seconds): Either[String, UserInfo] =
      Await.result(service.getUserInfo(authorisationCode, nino).value, waitTime)

  }

  "The UserInfoAPIService" when {
    val authorisationCode = "authorisation"

    val tokens = OAuthTokens("access", "refresh")

    val newTokens = OAuthTokens("new-access", "new-refresh")

    val error = "error"

    val userInfo = UserInfo(Some("Joe"), Some("Bloggs"), None, None, None, None, None, None)

    "getting user info" must {

      "exchange the authorisation code for an access token" in new TestApparatus{
        mockGetTokens(authorisationCode)(Left(error))
        getUserInfo(authorisationCode)
      }

      "use the access token to call the user info API" in new TestApparatus{
        inSequence {
          mockGetTokens(authorisationCode)(Right(tokens))
          mockGetUserInfo(tokens)(Left(UnknownError(error)))
        }
        getUserInfo(authorisationCode)
      }

      "return the user info when the connector gives a successful user info" in new TestApparatus{
        inSequence {
          mockGetTokens(authorisationCode)(Right(tokens))
          mockGetUserInfo(tokens)(Right(userInfo))
        }
        getUserInfo(authorisationCode) shouldBe Right(userInfo)
      }

      "refresh the access token if the API indicates that the token has expired" in new TestApparatus{
        inSequence {
          mockGetTokens(authorisationCode)(Right(tokens))
          mockGetUserInfo(tokens)(Left(TokenExpiredError))
          mockRefreshTokens(tokens)(Left(error))
        }

        getUserInfo(authorisationCode)
      }

      "use the new token to call the user info API again" in new TestApparatus{
        inSequence {
          mockGetTokens(authorisationCode)(Right(tokens))
          mockGetUserInfo(tokens)(Left(TokenExpiredError))
          mockRefreshTokens(tokens)(Right(newTokens))
          mockGetUserInfo(newTokens)(Left(UnknownError(error)))
        }

        getUserInfo(authorisationCode)
      }

      "return the user info by the user info API after using the new token" in new TestApparatus{
        inSequence {
          mockGetTokens(authorisationCode)(Right(tokens))
          mockGetUserInfo(tokens)(Left(TokenExpiredError))
          mockRefreshTokens(tokens)(Right(newTokens))
          mockGetUserInfo(newTokens)(Right(userInfo))
        }

        getUserInfo(authorisationCode) shouldBe Right(userInfo)
      }


      "return an error" when {

        "there is an error getting an access token" in new TestApparatus{
          inSequence {
            mockGetTokens(authorisationCode)(Left(error))
          }

          getUserInfo(authorisationCode).isLeft shouldBe true
        }

        "the user info API returns an error" in new TestApparatus{
          inSequence {
            mockGetTokens(authorisationCode)(Right(tokens))
            mockGetUserInfo(tokens)(Left(UnknownError(error)))
          }

          getUserInfo(authorisationCode).isLeft shouldBe true
        }

        "there is an error refreshing the token" in new TestApparatus{
          inSequence {
            mockGetTokens(authorisationCode)(Right(tokens))
            mockGetUserInfo(tokens)(Left(TokenExpiredError))
            mockRefreshTokens(tokens)(Left(error))
          }

          getUserInfo(authorisationCode).isLeft shouldBe true
        }

        "the user info API indicates new token acquired is still invalid" in new TestApparatus{
          inSequence {
            mockGetTokens(authorisationCode)(Right(tokens))
            mockGetUserInfo(tokens)(Left(TokenExpiredError))
            mockRefreshTokens(tokens)(Right(newTokens))
            mockGetUserInfo(newTokens)(Left(TokenExpiredError))
          }

          getUserInfo(authorisationCode).isLeft shouldBe true
        }

        "the user info API returns an error after a retry" in new TestApparatus{
          inSequence {
            mockGetTokens(authorisationCode)(Right(tokens))
            mockGetUserInfo(tokens)(Left(TokenExpiredError))
            mockRefreshTokens(tokens)(Right(newTokens))
            mockGetUserInfo(newTokens)(Left(UnknownError(error)))
          }

          getUserInfo(authorisationCode).isLeft shouldBe true
        }
      }
    }
  }

}
