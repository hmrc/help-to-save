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

import java.util.Base64

import configs.syntax._
import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.util.{Logging, WithMdcExecutionContext, toFuture}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class StrideAuth(htsAuthConnector: AuthConnector)(implicit val appConfig: AppConfig)
  extends BaseController with AuthorisedFunctions with Logging with WithMdcExecutionContext {

  override def authConnector: AuthConnector = htsAuthConnector

  private val requiredRoles: List[String] = {
    val decoder = Base64.getDecoder
    appConfig.runModeConfiguration.underlying
      .get[List[String]]("stride.base64-encoded-roles")
      .value
      .map(s ⇒ new String(decoder.decode(s)))
  }

  def authorisedFromStride(action: Request[AnyContent] ⇒ Future[Result]): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthProviders(PrivilegedApplication)).retrieve(allEnrolments) {
        enrolments ⇒
          val necessaryRoles: Option[List[Enrolment]] =
            requiredRoles.map(enrolments.getEnrolment).traverse[Option, Enrolment](identity)

          necessaryRoles.fold[Future[Result]](Unauthorized("Insufficient roles")) { _ ⇒ action(request) }
      }.recover {
        case _: NoActiveSession ⇒
          logger.warn("user is not logged in via stride, probably a hack?")
          Forbidden("no stride session found for logged in user")
      }
    }
}
