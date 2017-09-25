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

import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.util.{Logging, toFuture}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class HelpToSaveAuth(htsAuthConnector: HtsAuthConnector) extends BaseController with AuthorisedFunctions with Logging {

  override def authConnector: AuthConnector = htsAuthConnector

  private val NinoWithCL200: Enrolment = Enrolment("HMRC-NI").withConfidenceLevel(L200)

  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  private val AuthWithCL200: Predicate = NinoWithCL200 and AuthProvider

  private type HtsAction = Request[AnyContent] ⇒ Future[Result]

  def authorised(action: HtsAction): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthWithCL200)
        .retrieve(Retrievals.authorisedEnrolments) { authorisedEnrols ⇒

          val mayBeNino = authorisedEnrols.enrolments
            .find(_.key == "HMRC-NI")
            .flatMap(_.getIdentifier("NINO"))
            .map(_.value)

          mayBeNino.fold(
            toFuture(InternalServerError("could not find NINO for logged in user"))
          )(_ ⇒ action(request))
        }.recover {
        handleFailure()
      }
    }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      logger.warn("user is not logged in, probably hack? ")
      InternalServerError("")

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      logger.warn("unexpected: not met required ConfidenceLevel for logged in user")
      InternalServerError("")

    case ex: AuthorisationException ⇒
      logger.warn(s"could not authenticate user due to: $ex")
      InternalServerError("")
  }
}

