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

package uk.gov.hmrc.helptosave.controllers

import java.util.Base64

import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.PayePersonalDetailsConnector
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.EligibilityCheckService
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging}

class StrideController @Inject() (eligibilityCheckService:      EligibilityCheckService,
                                  payePersonalDetailsConnector: PayePersonalDetailsConnector,
                                  authConnector:                AuthConnector,
                                  enrolmentStore:               EnrolmentStore)(implicit transformer: LogMessageTransformer, override val appConfig: AppConfig)

  extends StrideAuth(authConnector) with Logging with WithMdcExecutionContext {

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def getEnrolmentStatus(nino: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒
    enrolmentStore.get(nino).fold(
      {
        e ⇒
          logger.warn(s"Could not get enrolments status: $e", nino)
          InternalServerError
      }, { status ⇒
        Ok(Json.toJson(status))
      }
    )
  }

  def eligibilityCheck(nino: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒
    eligibilityCheckService.getEligibility(nino).fold(
      {
        e ⇒
          logger.warn(s"Could not check eligibility: $e", nino)
          InternalServerError
      }, {
        r ⇒
          Ok(Json.toJson(r))
      }
    )

  }

  def getPayePersonalDetails(nino: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒
    payePersonalDetailsConnector.getPersonalDetails(nino)
      .fold(
        { error ⇒
          logger.warn(s"Could not retrieve paye-personal-details from DES: $error", nino)
          InternalServerError
        }, {
          r ⇒
            Ok(Json.toJson(r))
        }
      )
  }

}
