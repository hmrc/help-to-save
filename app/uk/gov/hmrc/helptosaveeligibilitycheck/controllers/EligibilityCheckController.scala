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

package uk.gov.hmrc.helptosaveeligibilitycheck.controllers

import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.helptosaveeligibilitycheck.connectors.HelpToSaveStubConnector
import uk.gov.hmrc.play.microservice.controller.BaseController


class EligibilityCheckController @Inject()(stubConnector: HelpToSaveStubConnector) extends BaseController {

	def eligibilityCheck(nino: String): Action[AnyContent] = Action.async { implicit request =>
		stubConnector.checkEligibility(nino).map(result ⇒ Ok(Json.toJson(result)))
	}

}
