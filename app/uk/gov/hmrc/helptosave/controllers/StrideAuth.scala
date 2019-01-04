/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.util.{Logging, toFuture}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class StrideAuth(htsAuthConnector: AuthConnector)(implicit val appConfig: AppConfig)
  extends BaseController with AuthorisedFunctions with Logging {

  override def authConnector: AuthConnector = htsAuthConnector

  private val (standardRoles, secureRoles): (List[String], List[String]) = {
    val decoder = Base64.getDecoder

      def getRoles(key: String): List[String] = appConfig.runModeConfiguration.underlying
        .get[List[String]](key)
        .value
        .map(s ⇒ new String(decoder.decode(s)))

    getRoles("stride.base64-encoded-roles") → getRoles("stride.base64-encoded-secure-roles")
  }

  private def roleMatch(enrolments: Enrolments): Boolean = {
    val enrolmentKeys = enrolments.enrolments.map(_.key)
    standardRoles.forall(enrolmentKeys.contains) || secureRoles.forall(enrolmentKeys.contains)
  }

  def authorisedFromStride(action: Request[AnyContent] ⇒ Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthProviders(PrivilegedApplication)).retrieve(allEnrolments) {
        enrolments ⇒
          if (roleMatch(enrolments)) {
            action(request)
          } else {
            Unauthorized("Insufficient roles")
          }
      }.recover {
        case _: NoActiveSession ⇒
          logger.warn("user is not logged in via stride, probably a hack?")
          Unauthorized
      }
    }

}
