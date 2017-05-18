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

import com.google.inject.Inject
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.connectors.NSIConnector
import uk.gov.hmrc.helptosave.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosave.models.{NSIUserInfo, UserInfo}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class NSIController @Inject()(nsiConnector: NSIConnector) extends BaseController {

  def createAccount: Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.map(_.validate[UserInfo]) match {
      case None ⇒
        Logger.error("We have failed to create an account due to having No Json ")
        Future.successful(BadRequest(
          Json.toJson(SubmissionFailure(None, "No json", ""))))
      case Some(er: JsError) ⇒
        Logger.error("We have failed to create an account due to invalid Json " + er.toString)
        Future.successful(BadRequest(
          Json.toJson(SubmissionFailure(None, "Invalid Json", er.errors.toString()))))
      case Some(JsSuccess(createAccount, _)) ⇒
        NSIUserInfo(createAccount).fold(
          errors ⇒ {
            Logger.info("We have failed to create an account due to invalid user details " + errors)
            Future.successful(BadRequest(
              Json.toJson(SubmissionFailure(None, "Invalid user details", errors.toList.mkString(",")))))
          },
          nSIUserInfo ⇒ {
            nsiConnector.createAccount(nSIUserInfo).flatMap {
              _ match {
                case SubmissionSuccess ⇒
                  Logger.debug("We have created an account on NSI ")
                  Future.successful(Created)
                case SubmissionFailure(_, message, detail) ⇒
                  Logger.error(s"We have failure to create an account on nsi (message - $message, details - $detail")
                  Future.successful(BadRequest)
              }
            }
          }
        )
    }
  }
}
