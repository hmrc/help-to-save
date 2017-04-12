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

package uk.gov.hmrc.helptosaveeligibilitycheck.connectors

import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{JsError, JsSuccess}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.helptosaveeligibilitycheck.WSHttp
import uk.gov.hmrc.helptosaveeligibilitycheck.models.UserDetails
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@ImplementedBy(classOf[HelpToSaveStubConnector])
trait HelpToSaveConnector {
  def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Future[UserDetails]
}

@Singleton
/**
  * Implements communication with help-to-save-stub
  */
class HelpToSaveStubConnector extends HelpToSaveConnector with ServicesConfig {

  private val helpToSaveStubURL: String = baseUrl("help-to-save-stub")

  // TODO: read from config?
  private def serviceURL(nino: String) = s"help-to-save-stub/eligibilitycheck/$nino"

  private val http = WSHttp

  override def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Future[UserDetails] =
    http.GET(s"$helpToSaveStubURL/${serviceURL(nino)}").flatMap{
      _.json.validate[UserDetails] match {
        case JsSuccess(user, _) ⇒ Future.successful(user)
        case JsError(_)         ⇒ Future.failed[UserDetails](new Exception("Could not parse user details"))
      }
    }
}
