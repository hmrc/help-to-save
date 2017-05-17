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

import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.helptosave.WSHttp
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@ImplementedBy(classOf[ApiTwentyFiveCConnectorImpl])
trait ApiTwentyFiveCConnector {
  def getAwards(nino: String)(implicit hc: HeaderCarrier): Future[List[Award]]
}

/**
  * Implements communication with help-to-save-stub
  */
@Singleton
class ApiTwentyFiveCConnectorImpl extends ServicesConfig  with ApiTwentyFiveCConnector{

  private val helpToSaveStubURL: String = baseUrl("help-to-save-stub")

  private def serviceURL(nino: String) = s"help-to-save-stub/edh/wtc/$nino"

  private val http = WSHttp

  def getAwards(nino: String)(implicit hc: HeaderCarrier): Future[List[Award]] =
    http.GET(s"$helpToSaveStubURL/${serviceURL(nino)}").flatMap {
      _.json.validate[ApiTwentyFiveCValues] match {
        case JsSuccess(result, _) ⇒ Future.successful(result.awards)
        case JsError(e) ⇒ Future.failed[List[Award]](new Exception("Could not parse awards " + e.head))
      }
    }
}
