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

import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.helptosave.services.EligibilityCheckService
import uk.gov.hmrc.helptosave.util.Logging.LoggerOps
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EligibilityBase extends Logging {

  val eligibilityCheckService: EligibilityCheckService

  def checkForNino(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, transformer: LogMessageTransformer): Future[Result] =
    eligibilityCheckService.getEligibility(nino).fold(
      {
        e ⇒
          logger.warn(s"Could not check eligibility due to $e", nino)
          InternalServerError
      }, r ⇒ Ok(Json.toJson(r))
    )
}
