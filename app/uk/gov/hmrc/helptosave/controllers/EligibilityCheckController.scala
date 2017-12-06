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

package uk.gov.hmrc.helptosave.controllers

import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.connectors.EligibilityCheckConnector
import uk.gov.hmrc.helptosave.util.{Logging, NINOLogMessageTransformer}
import uk.gov.hmrc.helptosave.util.Logging._

class EligibilityCheckController @Inject() (eligibilityCheckService: EligibilityCheckConnector,
                                            htsAuthConnector:        HtsAuthConnector)(
    implicit
    transformer: NINOLogMessageTransformer
)
  extends HelpToSaveAuth(htsAuthConnector) with Logging with WithMdcExecutionContext {

  def eligibilityCheck(): Action[AnyContent] = authorised { implicit request ⇒ implicit nino ⇒
    eligibilityCheckService.isEligible(nino).fold(
      {
        e ⇒
          logger.warn(s"Could not check eligibility: $e", nino)
          InternalServerError
      },
      r ⇒ Ok(Json.toJson(r))
    )
  }

}

