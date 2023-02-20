/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.http.scaladsl.model.HttpResponse
import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.repo.{EmailStore, EnrolmentStore}
import uk.gov.hmrc.helptosave.services.{BarsService, HelpToSaveService, UserCapService}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class HelpToSaveController @Inject() (val enrolmentStore:         EnrolmentStore,
                                      emailStore:                 EmailStore,
                                      proxyConnector:             HelpToSaveProxyConnector,
                                      userCapService:             UserCapService,
                                      val helpToSaveService:      HelpToSaveService,
                                      override val authConnector: AuthConnector,
                                      auditor:                    HTSAuditor,
                                      barsService:                BarsService,
                                      controllerComponents:       ControllerComponents)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig, ec: ExecutionContext)
  extends HelpToSaveAuth(authConnector, controllerComponents) with EligibilityBase with EnrolmentBehaviour {

  def createAccount(): Action[AnyContent] = ggOrPrivilegedAuthorised {
    implicit request ⇒
      val additionalParams = "apiCorrelationId" -> request.headers.get(appConfig.correlationIdHeaderName).getOrElse("-")

      request.body.asJson.map(_.validate[CreateAccountRequest](CreateAccountRequest.createAccountRequestReads(Some(createAccountVersion)))) match {
        case Some(JsSuccess(createAccountRequest, _)) ⇒
          val payload = createAccountRequest.payload
          val nino = payload.nino

          //delete any existing emails of DE users from mongo
          if (payload.contactDetails.communicationPreference === "00") {
            emailStore.delete(nino).fold(
              error ⇒ {
                logger.warn(s"couldn't delete email for DE user due to: $error", nino)
                toFuture(InternalServerError)
              },
              _ ⇒ createAccount(createAccountRequest, additionalParams)
            ).flatMap(identity)
          } else {
            createAccount(createAccountRequest, additionalParams)
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
      request.body.asJson.map(_.validate[NSIPayload](NSIPayload.nsiPayloadReads(None))) match {
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

  def doBarsCheck(): Action[AnyContent] = ggOrPrivilegedAuthorised { implicit request ⇒
    request.body.asJson.map(_.validate[BankDetailsValidationRequest]) match {

      case Some(JsSuccess(bankDetailsValidationRequest, _)) ⇒
        barsService.validate(bankDetailsValidationRequest).flatMap {
          case Right(result) ⇒
            Ok(Json.toJson(result))

          case Left(e) ⇒
            logger.warn("Could not perform bank details validation")
            InternalServerError
        }

      case Some(error: JsError) ⇒
        val errorString = error.prettyPrint()
        logger.warn(s"Could not parse bank details validation request body: $errorString")
        BadRequest

      case None ⇒
        logger.warn("No body found in bank details validation request")
        BadRequest
    }
  }

  private def createAccount(createAccountRequest: CreateAccountRequest,
                            additionalParams:     (String, String))(implicit hc: HeaderCarrier) = {
    val payload = createAccountRequest.payload
    val nino = payload.nino
    proxyConnector.createAccount(payload).map { response ⇒
      if (response.status === CREATED || response.status === CONFLICT) {
        Try {
          (response.json \ "accountNumber").as[String]
        } match {
          case Success(accountNumber) ⇒
            enrolUserAndHandleResult(createAccountRequest, additionalParams, nino, Some(accountNumber))
          case _ ⇒
            logger.warn("No account number was found in create account NSI response")
            enrolUserAndHandleResult(createAccountRequest, additionalParams, nino, None)
        }
      }

      if (response.status === CREATED) {
        auditor.sendEvent(AccountCreated(payload, createAccountRequest.source, createAccountRequest.detailsManuallyEntered), nino)

        userCapService.update().onComplete {
          case Success(_) ⇒ logger.debug("Successfully updated user cap counts after account creation", nino, additionalParams)
          case Failure(e) ⇒ logger.warn(s"Could not update user cap counts after account creation: ${e.getMessage}", nino, additionalParams)
        }
      }

      Option(response.body).fold[Result](Status(response.status))(body ⇒ Status(response.status)(body))
    }
  }

  def enrolUserAndHandleResult(createAccountRequest: CreateAccountRequest,
                               additionalParams:     (String, String),
                               nino:                 String,
                               optAccountNumber:     Option[String])(implicit hc: HeaderCarrier): Unit =
    enrolUser(createAccountRequest, optAccountNumber).value.onComplete {
      case Success(Right(_)) if optAccountNumber.isDefined ⇒
        logger.info("User was successfully enrolled into HTS and account number was persisted", nino, additionalParams)
      case Success(Right(_)) ⇒ logger.info("User was successfully enrolled into HTS", nino, additionalParams)
      case Success(Left(e))  ⇒ logger.warn(s"User was not enrolled: $e", nino, additionalParams)
      case Failure(e)        ⇒ logger.warn(s"User was not enrolled: ${e.getMessage}", nino, additionalParams)
    }

  private val createAccountVersion = appConfig.createAccountVersion

}
