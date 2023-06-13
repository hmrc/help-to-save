/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.LogMessageTransformer
import uk.gov.hmrc.helptosave.util.Logging._

import scala.concurrent.ExecutionContext

class PayePersonalDetailsController @Inject() (val helpToSaveService: HelpToSaveService,
                                               authConnector:         AuthConnector,
                                               controllerComponents:  ControllerComponents)(implicit transformer: LogMessageTransformer,
                                                                                            override val appConfig: AppConfig,
                                                                                            ec:                     ExecutionContext)

  extends StrideAuth(authConnector, controllerComponents) with EligibilityBase {

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def getPayePersonalDetails(nino: String): Action[AnyContent] = authorisedFromStride { implicit request =>
    (for {
      r <- helpToSaveService.getPersonalDetails(nino)
    } yield Ok(Json.toJson(r))).valueOrF{
      error =>
        logger.warn(s"Could not retrieve paye-personal-details from DES: $error", nino)
        InternalServerError
    }
  }

}
