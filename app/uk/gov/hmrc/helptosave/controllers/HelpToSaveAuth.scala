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

import cats.data.ValidatedNel
import cats.syntax.cartesian._
import cats.syntax.either._
import cats.syntax.option._
import org.joda.time.LocalDate
import play.api.mvc._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future

class HelpToSaveAuth(app: Application, htsAuthConnector: HtsAuthConnector)
  extends AuthorisedFunctions with ServicesConfig with Logging {

  override def authConnector: AuthConnector = htsAuthConnector

//  override def config: Configuration = app.configuration
//
//  override def env: Environment = Environment(app.path, app.classloader, app.mode)

 private val ggLoginUrl = getString("microservice.services.company-auth-frontend.url")

  private val origin = getString("appName")

  private type HtsAction = Request[AnyContent] ⇒ Future[Result]

  def authorisedForHtsWithInfo(action: Request[AnyContent] ⇒ Future[Result])(redirectOnLoginURL: String): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthWithCL200)
        .retrieve(UserRetrievals and authorisedEnrolments) {
          case name ~ email ~ dateOfBirth ~ itmpName ~ itmpDateOfBirth ~ itmpAddress ~ authorisedEnrols ⇒

            val mayBeNino = authorisedEnrols.enrolments
              .find(_.key === "HMRC-NI")
              .flatMap(_.getIdentifier("NINO"))
              .map(_.value)

            mayBeNino.fold(
              toFuture(InternalServerError("could not find NINO for logged in user"))
            )(nino ⇒ {
                val userDetails = getUserInfo(nino, name, email, dateOfBirth, itmpName, itmpDateOfBirth, itmpAddress)
                action(request)(HtsContext(Some(nino), Some(userDetails.map(NSIUserInfo.apply)), isAuthorised = true))
              })

        }.recover {
          handleFailure(redirectOnLoginURL)
        }
    }


  def authorisedForHtsWithCL200(action: HtsAction)(redirectOnLoginURL: String): Action[AnyContent] = {
    Action.async { implicit request ⇒
      authorised(AuthWithCL200) {
        action(request)(HtsContext(isAuthorised = true))
      }.recover {
        handleFailure(redirectOnLoginURL)
      }
    }
  }

  def handleFailure(redirectOnLoginURL: String): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      redirectToLogin(redirectOnLoginURL)

    case _: InsufficientConfidenceLevel | _: InsufficientEnrolments ⇒
      toPersonalIV(s"$identityCallbackUrl?continueURL=${encoded(redirectOnLoginURL)}", ConfidenceLevel.L200)

    case ex: AuthorisationException ⇒
      logger.error(s"could not authenticate user due to: $ex")
      InternalServerError("")
  }

  private def redirectToLogin(redirectOnLoginURL: String) = Results.Redirect(ggLoginUrl, Map(
    "continue" -> Seq(redirectOnLoginURL),
    "accountType" -> Seq("individual"),
    "origin" -> Seq(origin)
  ))
}

