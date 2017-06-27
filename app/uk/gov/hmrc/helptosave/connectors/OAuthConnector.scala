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
import configs.syntax._
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.models.OAuthTokens
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[OAuthConnectorImpl])
trait OAuthConnector {

  def getToken(authorisationCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[OAuthTokens]

  def refreshToken(refreshToken: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[OAuthTokens]

}

@Singleton
class OAuthConnectorImpl @Inject()(configuration: Configuration) extends OAuthConnector {
  import uk.gov.hmrc.helptosave.connectors.OAuthConnectorImpl._

  private[connectors] val oauthConfig = configuration.underlying.get[OAuthTokenConfiguration]("oauth").value

  val http = new WSHttp

  override def getToken(authorisationCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[OAuthTokens] = call(
    Map(
      "redirect_uri" -> Seq(oauthConfig.callbackURL),
      "grant_type" -> Seq("authorization_code"),
      "code" -> Seq(authorisationCode)
    ))

  override def refreshToken(refreshToken: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[OAuthTokens] = call(
    Map(
      "grant_type" -> Seq("refresh_token"),
      "refresh_token" -> Seq(refreshToken)
    ))

  private def call(body: Map[String, Seq[String]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[OAuthTokens] = {
    val data =  Map(
      "client_id" → Seq(oauthConfig.client.id),
      "client_secret" → Seq(oauthConfig.client.secret)
    ) ++ body

    EitherT[Future,String,OAuthTokens](http.postForm(oauthConfig.url, data).map{ response ⇒
      response.status match {
        case Status.OK ⇒
          Try(response.json).map(_.validate[OAuthResponse]) match {
            case Success(JsSuccess(tokens, _)) =>
              Right(OAuthTokens(tokens.access_token, tokens.refresh_token))

            case Success(e: JsError) =>
              Left(s"Could not parse JSON response from OAuth. Response body was ${response.body}. ${e.prettyPrint()}")

            case Failure(e) =>
              Left(s"Could not parse OAuth response as JSON. Response body was ${response.body}. ${e.getMessage}")
          }

        case other ⇒
          Left(s"Call to OAuth came back with status $other. Response body was ${response.body}")
      }
    })
  }

}

object OAuthConnectorImpl {

  private[connectors] case class Client(id: String, secret: String)

  private[connectors] case class OAuthTokenConfiguration(url: String, client: Client, callbackURL: String)

  private[connectors] case class OAuthResponse(access_token: String, refresh_token: String, expires_in: Long)

  private[connectors] implicit val oauthResponseFormat: Format[OAuthResponse] = Json.format[OAuthResponse]

}