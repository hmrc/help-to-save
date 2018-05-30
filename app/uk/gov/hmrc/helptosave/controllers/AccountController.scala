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

import java.util.UUID

import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, WithMdcExecutionContext}

class AccountController @Inject() (proxyConnector: HelpToSaveProxyConnector,
                                   authConnector:  AuthConnector)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends HelpToSaveAuth(authConnector) with WithMdcExecutionContext {

  def getAccount(nino: String, systemId: String, correlationId: Option[String]): Action[AnyContent] = authorised { implicit request ⇒ implicit authNino ⇒
    if (!uk.gov.hmrc.domain.Nino.isValid(nino)) {
      logger.warn("NINO in request was not valid")
      BadRequest
    } else if (nino =!= authNino) {
      logger.warn("NINO in request did not match NINO found in auth")
      Forbidden
    } else {
      val id = correlationId.getOrElse(UUID.randomUUID().toString)
      proxyConnector.getAccount(nino, systemId, id)
        .fold(
          { e ⇒
            logger.warn(e)
            InternalServerError
          },
          _.fold[Result](NotFound)(account ⇒ Ok(Json.toJson(account)))
        )
    }
  }
}
