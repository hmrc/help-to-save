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

import com.google.inject.Inject
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, WithMdcExecutionContext}

class AccountController @Inject() (proxyConnector: HelpToSaveProxyConnector,
                                   authConnector:  AuthConnector)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends HelpToSaveAuth(authConnector) with WithMdcExecutionContext with AccountQuery {

  def getAccount(nino: String, systemId: String, correlationId: Option[String]): Action[AnyContent] =
    accountQuery(nino, systemId, correlationId) { implicit request ⇒ (nino, systemId, id) ⇒
      proxyConnector.getAccount(nino, systemId, id)
    }
}
