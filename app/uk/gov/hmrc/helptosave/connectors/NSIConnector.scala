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

import javax.inject.Singleton

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosave.WSHttpProxy
import uk.gov.hmrc.helptosave.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosave.models.NSIUserInfo
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Either[SubmissionFailure, SubmissionSuccess]]
}

object NSIConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(message: String) extends SubmissionResult

}

@Singleton
class NSIConnectorImpl extends NSIConnector with ServicesConfig {

  import uk.gov.hmrc.helptosave.connectors.NSIConnectorImpl._

  val nsiUrl: String = baseUrl("nsi")
  val nsiUrlEnd: String = getString("microservice.services.nsi.url")
  val url = s"$nsiUrl/$nsiUrlEnd"
  val encodedAuthorisation: String = {
    val userName: String = getString("microservice.services.nsi.username")
    val password: String = getString("microservice.services.nsi.password")
    BaseEncoding.base64().encode((userName + ":" + password).getBytes(Charsets.UTF_8))
  }

  val httpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Either[SubmissionFailure, SubmissionSuccess]] = {
    Logger.debug(s"About to create a account for ${userInfo.NINO}")
    httpProxy.post(url, userInfo, headers = Seq(("Authorization1", encodedAuthorisation)))
      .map { response ⇒
        response.status match {
          case Status.CREATED ⇒
            Logger.debug(s"Successfully created a NSI account for ${userInfo.NINO}")
            Right(SubmissionSuccess())

          case Status.BAD_REQUEST ⇒
            Logger.error("We have failed to make an account due to a bad request")
            Left(SubmissionFailure(getBadRequestError(response)))

          case other ⇒
            Logger.warn(s"Create account call for ${userInfo.NINO} to NSI came back with status $other")
            Left(SubmissionFailure(s"Create account call to NSI came back with status $other. Response body was: ${response.body}"))
        }
      }
  }

  private def getBadRequestError(response: HttpResponse): String = {
    Try(response.json) match {
      case Success(jsValue) ⇒
        Json.fromJson[NSISubmissionFailure](jsValue) match {
          case JsSuccess(f, _) ⇒
            s"Bad create account request made to NSI: [id: ${f.errorMessageId}, " +
              s" message: ${f.errorMessage}, details: ${f.errorDetail}]"

          case e: JsError ⇒
            "Bad create account request made to NSI but could not parse NSI error " +
              s"response: ${e.prettyPrint()}. Response body was ${response.body}"
        }

      case Failure(_) ⇒
        s"Bad create account request made to NSI but response did not contain JSON. Response body was ${response.body}"

    }
  }
}

object NSIConnectorImpl {

  private[connectors] case class NSISubmissionFailure(errorMessageId: String,
                                                      errorMessage: String,
                                                      errorDetail: String) extends SubmissionResult

  private[connectors] implicit val nsiSubmissionFailureFormat: Format[NSISubmissionFailure] = Json.format[NSISubmissionFailure]
}