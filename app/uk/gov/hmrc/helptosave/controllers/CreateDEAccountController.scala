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
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.{HelpToSaveProxyConnector, ITMPEnrolmentConnector}
import uk.gov.hmrc.helptosave.models.{ErrorResponse, NSIUserInfo}
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.UserCapService
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, toFuture}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.util.{Failure, Success}

class CreateDEAccountController @Inject() (val enrolmentStore: EnrolmentStore,
                                           val itmpConnector:  ITMPEnrolmentConnector,
                                           proxyConnector:     HelpToSaveProxyConnector,
                                           userCapService:     UserCapService)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig)
  extends BaseController with Logging with WithMdcExecutionContext with EnrolmentBehaviour {

  def createDEAccount(): Action[AnyContent] = Action.async {
    implicit request ⇒
      request.body.asJson.map(_.validate[NSIUserInfo]) match {
        case Some(JsSuccess(userInfo, _)) ⇒
          proxyConnector.createAccount(userInfo)
            .map { response ⇒
              if (response.status === CREATED) {

                val additionalParams = "apiCorrelationId" -> request.headers.get(appConfig.correlationIdHeaderName).getOrElse("-")

                enrolUser(userInfo.nino).value.onComplete {
                  case Success(Right(_)) ⇒ logger.info("User was successfully enrolled into HTS", userInfo.nino, additionalParams)
                  case Success(Left(e))  ⇒ logger.warn(s"User was not enrolled: $e", userInfo.nino, additionalParams)
                  case Failure(e)        ⇒ logger.warn(s"User was not enrolled: ${e.getMessage}", userInfo.nino, additionalParams)
                }

                userCapService.update().onComplete {
                  case Success(_) ⇒ logger.debug("Sucessfully updated user cap counts after DE account created", userInfo.nino, additionalParams)
                  case Failure(e) ⇒ logger.warn(s"Could not update user cap counts after DE account created: ${e.getMessage}", userInfo.nino, additionalParams)
                }

              }
              Option(response.body).fold[Result](Status(response.status))(body ⇒ Status(response.status)(body))
            }

        case Some(error: JsError) ⇒
          val errorString = error.prettyPrint()
          logger.warn(s"Could not parse JSON in request body: $errorString")
          BadRequest(ErrorResponse("Could not parse JSON in request", errorString).toJson())

        case None ⇒
          logger.warn("No JSON body found in request")
          BadRequest(ErrorResponse("No JSON found in request body", "").toJson())
      }
  }
}
