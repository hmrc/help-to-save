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

import java.net.URLDecoder

import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.helptosave.services.UserInfoService
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError._
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext

class UserInformationController @Inject()(userInfoService: UserInfoService)(implicit ec: ExecutionContext) extends BaseController {

  val logger = Logger(this.getClass)

  def getUserInformation(nino: NINO, userDetailsURI: String): Action[AnyContent] = Action.async { implicit request ⇒
    val urlDecoded = URLDecoder.decode(userDetailsURI, "UTF-8")
    userInfoService.getUserInfo(urlDecoded, nino).fold(handleError, u ⇒ Ok(Json.toJson(u)))
  }

  private def handleError(error: UserInfoServiceError): Result = {
    def errorMessage(message: String): String = s"Could not perform eligibility check - $message"

    error match {
      case UserDetailsError(message) ⇒
        logger.warn(errorMessage(s"error encountered in the user details service: $message"))
        InternalServerError

      case CitizenDetailsError(message) ⇒
        logger.warn(errorMessage(s"error encountered in the citizen details service: $message"))
        InternalServerError

      case m: MissingUserInfos ⇒
       Ok(Json.toJson(m))

    }
  }

}


