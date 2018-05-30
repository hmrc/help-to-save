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

import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.helptosave.util.{Logging, NINO, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

object HelpToSaveAuth {

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val AuthWithCL200: Predicate = AuthProvider and ConfidenceLevel.L200

}

class HelpToSaveAuth(htsAuthConnector: AuthConnector) extends BaseController with AuthorisedFunctions with Logging with WithMdcExecutionContext {

  import HelpToSaveAuth._

  override def authConnector: AuthConnector = htsAuthConnector

  private type HtsAction = Request[AnyContent] ⇒ NINO ⇒ Future[Result]

  def authorised(action: HtsAction): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthWithCL200)
        .retrieve(Retrievals.nino) { mayBeNino ⇒
          mayBeNino.fold[Future[Result]](
            Forbidden
          )(nino ⇒ action(request)(nino)
          )
        }.recover {
          handleFailure()
        }
    }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      logger.warn("user is not logged in, probably a hack?")
      Unauthorized

    case e: InternalError ⇒
      logger.warn(s"Could not authenticate user due to internal error: ${e.reason}")
      InternalServerError

    case ex: AuthorisationException ⇒
      logger.warn(s"could not authenticate user due to: ${ex.reason}")
      Forbidden
  }
}

