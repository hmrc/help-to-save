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

import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.models.{AccountCreated, ErrorResponse, NSIPayload}
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.{HelpToSaveService, UserCapService}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, WithMdcExecutionContext, toFuture}

import scala.util.{Failure, Success}

class HelpToSaveController @Inject() (val enrolmentStore:         EnrolmentStore,
                                      proxyConnector:             HelpToSaveProxyConnector,
                                      userCapService:             UserCapService,
                                      val helpToSaveService:      HelpToSaveService,
                                      override val authConnector: AuthConnector,
                                      auditor:                    HTSAuditor)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig)
  extends HelpToSaveAuth(authConnector) with EligibilityBase with EnrolmentBehaviour with WithMdcExecutionContext {

  def createAccount(): Action[AnyContent] = ggOrPrivilegedAuthorised {
    implicit request ⇒
      val additionalParams = "apiCorrelationId" -> request.headers.get(appConfig.correlationIdHeaderName).getOrElse("-")

      request.body.asJson.map(_.validate[CreateAccountRequest]) match {
        case Some(JsSuccess(createAccountRequest, _)) ⇒
          val payload = createAccountRequest.payload
          proxyConnector.createAccount(payload).map { response ⇒
            if (response.status === CREATED || response.status === CONFLICT) {
              enrolUser(createAccountRequest).value.onComplete {
                case Success(Right(_)) ⇒ logger.info("User was successfully enrolled into HTS", payload.nino, additionalParams)
                case Success(Left(e))  ⇒ logger.warn(s"User was not enrolled: $e", payload.nino, additionalParams)
                case Failure(e)        ⇒ logger.warn(s"User was not enrolled: ${e.getMessage}", payload.nino, additionalParams)
              }
            }

            if (response.status === CREATED) {
              auditor.sendEvent(AccountCreated(payload, createAccountRequest.source), payload.nino)

              userCapService.update().onComplete {
                case Success(_) ⇒ logger.debug("Successfully updated user cap counts after account creation", payload.nino, additionalParams)
                case Failure(e) ⇒ logger.warn(s"Could not update user cap counts after account creation: ${e.getMessage}", payload.nino, additionalParams)
              }
            }
            Option(response.body).fold[Result](Status(response.status))(body ⇒ Status(response.status)(body))
          }

        case Some(error: JsError) ⇒
          val errorString = error.prettyPrint()
          logger.warn(s"Could not parse NSIUserInfo JSON in request body: $errorString")
          BadRequest(ErrorResponse("Could not parse NSIUserInfo JSON in request", errorString).toJson())

        case None ⇒
          logger.warn("No JSON body found in request")
          BadRequest(ErrorResponse("No JSON found in request body", "").toJson())
      }
  }

  def updateEmail(): Action[AnyContent] = ggOrPrivilegedAuthorised {
    implicit request ⇒
      request.body.asJson.map(_.validate[NSIPayload]) match {
        case Some(JsSuccess(userInfo, _)) ⇒
          proxyConnector.updateEmail(userInfo).map { response ⇒
            Option(response.body).fold[Result](Status(response.status))(body ⇒ Status(response.status)(body))
          }

        case Some(error: JsError) ⇒
          val errorString = error.prettyPrint()
          logger.warn(s"Could not parse NSIUserInfo JSON in request body: $errorString")
          BadRequest(ErrorResponse("Could not parse NSIUserInfo JSON in request", errorString).toJson())

        case None ⇒
          logger.warn("No JSON body found in request")
          BadRequest(ErrorResponse("No JSON found in request body", "").toJson())
      }
  }

  def checkEligibility(nino: String): Action[AnyContent] = ggOrPrivilegedAuthorised {
    implicit request ⇒ checkEligibility(nino, routes.HelpToSaveController.checkEligibility(nino).url)
  }
}
