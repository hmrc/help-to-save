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
import uk.gov.hmrc.helptosave.connectors.{HelpToSaveProxyConnector, ITMPEnrolmentConnector}
import uk.gov.hmrc.helptosave.models.{ErrorResponse, NSIUserInfo}
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, toFuture}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.util.{Failure, Success}

class CreateDEAccountController @Inject() (val enrolmentStore: EnrolmentStore,
                                           val itmpConnector:  ITMPEnrolmentConnector,
                                           proxyConnector:     HelpToSaveProxyConnector)(
    implicit
    transformer: LogMessageTransformer)
  extends BaseController with Logging with WithMdcExecutionContext with EnrolmentBehaviour with ServicesConfig {

  lazy val correlationIdHeaderName: String = getString("microservice.correlationIdHeaderName")

  def createDEAccount(): Action[AnyContent] = Action.async {
    implicit request ⇒
      request.body.asJson.map(_.validate[NSIUserInfo]) match {
        case Some(JsSuccess(userInfo, _)) ⇒
          proxyConnector.createAccount(userInfo)
            .map { response ⇒
              if (response.status === CREATED) {

                val correlationId = request.headers.get(correlationIdHeaderName)

                enrolUser(userInfo.nino).value.onComplete {
                  case Success(Right(_)) ⇒ logger.debug("User was successfully enrolled into HTS", userInfo.nino, correlationId)
                  case Success(Left(e))  ⇒ logger.warn(s"User was not enrolled: $e", userInfo.nino, correlationId)
                  case Failure(e)        ⇒ logger.warn(s"User was not enrolled: ${e.getMessage}", userInfo.nino, correlationId)
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
