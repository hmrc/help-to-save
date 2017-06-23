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
import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import uk.gov.hmrc.helptosave.connectors.{OAuthConnector, UserInfoAPIConnector}
import uk.gov.hmrc.helptosave.connectors.UserInfoAPIConnector.{TokenExpiredError, UnknownError}
import uk.gov.hmrc.helptosave.models.userinfoapi.{OAuthTokens, APIUserInfo}
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UserInfoAPIService @Inject()(userInfoAPIConnector: UserInfoAPIConnector,
                                   oAuthConnector: OAuthConnector) {

  def getUserInfo(authorisationCode: String, nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[APIUserInfo] = for{
    tokens ← oAuthConnector.getToken(authorisationCode)
    userInfo ← getUserInfoWithRetry(tokens, nino)
  } yield userInfo

  /**
    * Uses the `userInfoAPIConnector` to get the user info. If the token used has expired
    * refresh the token and try again.
    */
  private def getUserInfoWithRetry(tokens: OAuthTokens, nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[APIUserInfo] = {
    val result: Future[Either[String, APIUserInfo]] = userInfoAPIConnector.getUserInfo(tokens).fold(
      {
        case TokenExpiredError     ⇒
          Logger.info(s"Access token for user info API has expired - refreshing token (nino: $nino)")
          refreshTokensAndRetry(tokens).value
        case UnknownError(message) ⇒
          Future.successful(Left(message))
      },
      info ⇒ Future.successful(Right(info))
    ).flatMap(identity)

    EitherT(result)
  }

  private def refreshTokensAndRetry(tokens: OAuthTokens)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[APIUserInfo] =
    for {
      newTokens ← oAuthConnector.refreshToken(tokens.refreshToken)
      userInfo ← userInfoAPIConnector.getUserInfo(newTokens).leftMap{
        case TokenExpiredError     ⇒ "Token retrieved after refreshing token was invalid"
        case UnknownError(message) ⇒ message
      }
    } yield userInfo


}
