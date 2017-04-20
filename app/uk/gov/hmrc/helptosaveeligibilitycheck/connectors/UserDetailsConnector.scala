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

import javax.inject.Singleton

import com.google.inject.ImplementedBy
import play.api.libs.json._
import uk.gov.hmrc.helptosaveeligibilitycheck.WSHttp
import uk.gov.hmrc.helptosaveeligibilitycheck.models.{Email, EmailException}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[UserDetailsConnectorImpl])
trait UserDetailsConnector {
  def getEmail(userDetailsId: String)(implicit hc: HeaderCarrier): Future[Email]
}

@Singleton
class UserDetailsConnectorImpl extends UserDetailsConnector with ServicesConfig {

  private val userDetailsRoot = baseUrl("user-details")
  private val serviceURL = "user-details/id"
  private val http = WSHttp

  /**
    * userDetailsId : this is the id that can be read from user-details snapshot data once the user is logged into GG
    * The auth response from GG contains this id , but we need to do more research once we integrate with GG
    */
  override def getEmail(userDetailsId: String)(implicit hc: HeaderCarrier): Future[Email] = {

    http.GET(s"$userDetailsRoot/$serviceURL/$userDetailsId").flatMap {
      _.json.validate[Email] match {
        case JsSuccess(email, _) ⇒ Future.successful(email)
        case JsError(ex) ⇒ Future.failed[Email](EmailException(userDetailsId, ex.toString()))
      }
    }
  }
}
