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
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]
}

object NSIConnector {

  sealed trait SubmissionResult

  case object SubmissionSuccess extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}


@Singleton
class NSIConnectorImpl extends NSIConnector with ServicesConfig {

  val nsiUrl: String = baseUrl("nsi")
  val nsiUrlEnd: String = getString("microservice.services.nsi.url")
  val url = s"$nsiUrl/$nsiUrlEnd"
  val encodedAuthorisation: String = {
    val userName: String = getString("microservice.services.nsi.username")
    val password: String = getString("microservice.services.nsi.password")
    BaseEncoding.base64().encode((userName + ":" + password).getBytes(Charsets.UTF_8))
  }

  val httpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {
    Logger.debug(s"We are trying to create a account for ${userInfo.NINO}")
    httpProxy.post(url, userInfo, headers = Seq(("Authorization1", encodedAuthorisation)))
      .map { response ⇒
        response.status match {
          case Status.CREATED ⇒
            Logger.debug("We have successfully created a NSI account")
            SubmissionSuccess

          case Status.BAD_REQUEST ⇒
            handleBadRequestResponse(response)

          case other ⇒
            Logger.warn(s"Something went wrong nsi ${userInfo.NINO}")
            SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
        }
      }
  }


  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    Logger.error("We have failed to make an account due to a bad request")
    Try(response.json) match {
      case Success(jsValue) ⇒
        Json.fromJson[SubmissionFailure](jsValue) match {
          case JsSuccess(submissionFailure, _) ⇒
            submissionFailure

          case e: JsError ⇒
            SubmissionFailure(None, s"Could not create NSI account errors; response body: ${response.body}", e.prettyPrint())
        }

      case Failure(error) ⇒
        SubmissionFailure(None, s"Could not read submission failure JSON response: ${response.body}", error.getMessage)

    }

  }
}
