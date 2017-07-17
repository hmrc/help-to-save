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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.connectors.UserInfoAPIConnector.{APIError, TokenExpiredError, UnknownError}
import uk.gov.hmrc.helptosave.models.OpenIDConnectUserInfo.Address
import uk.gov.hmrc.helptosave.models.{OAuthTokens, OpenIDConnectUserInfo}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[UserInfoAPIConnectorImpl])
trait UserInfoAPIConnector {

  def getUserInfo(input: OAuthTokens)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future,APIError,OpenIDConnectUserInfo]

}

object UserInfoAPIConnector {

  /** Something has gone wrong while calling the user info API */
  sealed trait APIError

  /** The token used to call the API has expired  */
  case object TokenExpiredError extends APIError

  /** Something else has happened */
  case class UnknownError(message: String) extends APIError

}


@Singleton
class UserInfoAPIConnectorImpl @Inject()(configuration: Configuration, ec: ExecutionContext) extends UserInfoAPIConnector {

  import UserInfoAPIConnectorImpl._

  private val config = configuration.underlying.getConfig("api.user-info")

  val url: String = config.getString("url")

  val acceptHeader: Map[String,String] = Map("Accept" → "application/vnd.hmrc.1.0+json")

  val authorisationHeaderKey: String = config.getString("authorisation-header-key")

  val http: WSHttp = new WSHttp

  def getUserInfo(input: OAuthTokens)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future,APIError,OpenIDConnectUserInfo] = {
    val headers = acceptHeader.updated(authorisationHeaderKey, s"Bearer ${input.accessToken}")

    EitherT[Future,APIError,OpenIDConnectUserInfo](http.get(url, headers)(hc, ec).map{ response ⇒
      response.status match {
        case Status.OK ⇒
          handleOKResponse(response)
        case Status.UNAUTHORIZED ⇒
          handleUnauthorisedResponse(response)
        case other ⇒
          failure(s"Call to user info API came back with status $other. Response body was ${response.body}")
      }
    }.recover {
      case e ⇒
        failure(s"Error while calling user info API: ${e.getMessage}")
    })
  }

  private def returnUserInfoWithCC(userInfo: OpenIDConnectUserInfo): OpenIDConnectUserInfo = {
    val addressWithCountry : Option[Address] =  userInfo.address match {
      case Some(address) ⇒
        val code1 = address.code match {
          case Some(codee) ⇒ codee
          case _ ⇒ "GB"
        }
        Some(address.copy(code = Some(code1)))
      case _ ⇒ None
    }

    userInfo.copy(address =  addressWithCountry)

  }

  private def handleOKResponse(response: HttpResponse): Either[APIError,OpenIDConnectUserInfo] =
    Try(response.json).map(_.validate[OpenIDConnectUserInfo]) match {
      case Success(JsSuccess(userInfo, _)) ⇒
        Right(returnUserInfoWithCC(userInfo))
      case Success(error: JsError) ⇒
        failure(s"Could not parse JSON response from user info API: ${error.prettyPrint()}")
      case Failure(_) ⇒
        failure(s"Response from user info API was not JSON. Response body was ${response.body}")
    }


  private def handleUnauthorisedResponse(response: HttpResponse): Either[APIError,OpenIDConnectUserInfo] = {
    val errorString = s"Call to user info API came back with status ${Status.UNAUTHORIZED} (unauthorised)"

    Try(response.json).map(_.validate[Error]) match {
      case Success(JsSuccess(error, _)) ⇒
        if(error.code == "INVALID_CREDENTIALS"){
          Left(TokenExpiredError)
        } else {
          failure(s"$errorString. Error code was ${error.code} and error message was ${error.message.getOrElse("-")}")
        }

      case Success(error: JsError) ⇒
        failure(s"$errorString but could not parse JSON response: ${error.prettyPrint()}")

      case Failure(_) ⇒
        failure(s"$errorString but response from user info API was not JSON. Response body was ${response.body}")
    }
  }

  private def failure(message: String): Either[APIError,OpenIDConnectUserInfo] = Left(UnknownError(message))

}

object UserInfoAPIConnectorImpl {

  private case class Error(code: String, message: Option[String])

  private implicit val errorFormat: Format[Error] = Json.format[Error]

}
