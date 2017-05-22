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

import java.net.{URI, URLDecoder, URLEncoder}

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result ⇒ _}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, UserInfo}
import uk.gov.hmrc.helptosave.services.{EligibilityCheckerService, UserInfoService}
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}


class EligibilityCheckController @Inject()(eligibilityCheckService: EligibilityCheckerService,
																					 userInfoService: UserInfoService)(implicit ec: ExecutionContext) extends BaseController {

	def eligibilityCheck(nino: NINO, userDetailsURI: String): Action[AnyContent] = Action.async { implicit request ⇒
		val result: Result[Option[UserInfo]] =
			eligibilityCheckService.getEligibility(nino).flatMap{ isEligible ⇒
				if(isEligible){
					val urlDecoded = URLDecoder.decode(userDetailsURI, "UTF-8")
					userInfoService.getUserInfo(urlDecoded, nino).map(Some(_): Option[UserInfo])
				} else {
					EitherT.pure[Future,String,Option[UserInfo]](None)
				}
			}

		result.fold(
			error ⇒ {
				Logger.error(s"Could not perform eligibility check: $error")
				InternalServerError(error)
			},
			userInfo ⇒ Ok(Json.toJson(EligibilityCheckResult(userInfo)))
		)
	}

}
