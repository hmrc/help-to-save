/*
 * Copyright 2018 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.mvc.Http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.models.{ErrorResponse, NSIUserInfo}
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveProxyConnectorImpl])
trait HelpToSaveProxyConnector {

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class HelpToSaveProxyConnectorImpl @Inject() (http: WSHttp)
  extends HelpToSaveProxyConnector with ServicesConfig with Logging {

  val proxyURL: String = baseUrl("help-to-save-proxy")

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    http.post(s"$proxyURL/help-to-save-proxy/create-account", userInfo)
      .recover {
        case e ⇒
          logger.warn(s"unexpected error from proxy during /create-de-account, message=${e.getMessage}")
          val errorJson = ErrorResponse("unexpected error from proxy during /create-de-account", s"${e.getMessage}").toJson()
          HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(errorJson))
      }
  }
}
