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
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, AnyContent, Result ⇒ PlayResult}
import uk.gov.hmrc.helptosave.connectors.NSIConnector
import uk.gov.hmrc.helptosave.models.{NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class NSIController @Inject()(nsiConnector: NSIConnector) extends BaseController {

  implicit def toFuture(result: PlayResult): Future[PlayResult] = Future.successful(result)

  def createAccount: Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.map(_.validate[UserInfo]) match {
      case None ⇒
        Logger.error("Create account failed - no JSON found in request")
        BadRequest("No JSON found in request")

      case Some(er: JsError) ⇒
        Logger.error("Create account failed - could not parse JSON in request: " + er.prettyPrint())
        BadRequest(er.prettyPrint())

      case Some(JsSuccess(userInfo, _)) ⇒
       createAccount(userInfo)
    }
  }

  private def createAccount(userInfo: UserInfo)(implicit hc: HeaderCarrier): Future[PlayResult] = {
    NSIUserInfo(userInfo).fold(
      // NSI validation checks have failed in this case
      errors ⇒ {
        Logger.info(s"Create an account - invalid user details: ${errors.toList.mkString(", ")}")
        BadRequest(errors.toList.mkString(", "))
      },
      nSIUserInfo ⇒ nsiConnector.createAccount(nSIUserInfo).map(_.fold[PlayResult](
        // NSI have come back with a response indicating the creation failed
        {error ⇒
          Logger.error(s"Error creating account with NSI: ${error.message}")
          InternalServerError("")
        },
        _ ⇒ Created("")
      ))
    )
  }
}
