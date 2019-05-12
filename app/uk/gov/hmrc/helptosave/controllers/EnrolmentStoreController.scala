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

import cats.data.EitherT
import cats.data.EitherT._
import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.models.account.AccountNumber
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps.getApiCorrelationId
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, NINO}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EnrolmentStoreController @Inject() (val enrolmentStore:    EnrolmentStore,
                                          val helpToSaveService: HelpToSaveService,
                                          authConnector:         AuthConnector,
                                          proxyConnector:        HelpToSaveProxyConnector)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig, ec: ExecutionContext)
  extends HelpToSaveAuth(authConnector) with EnrolmentBehaviour with AccountQuery {

  import EnrolmentStoreController._

  implicit val correlationIdHeaderName: String = appConfig.correlationIdHeaderName

  def setITMPFlag(): Action[AnyContent] = ggAuthorisedWithNino { implicit request ⇒ implicit nino ⇒
    handle(setITMPFlagAndUpdateMongo(nino), "set ITMP flag", nino)
  }

  def getAccountNumber(): Action[AnyContent] = ggAuthorisedWithNino { implicit request ⇒ implicit nino ⇒
    handleAccountNumber(enrolmentStore.getAccountNumber(nino), "get account number", nino, request.uri)
  }

  def getEnrolmentStatus(maybeNino: Option[String]): Action[AnyContent] = ggOrPrivilegedAuthorisedWithNINO(maybeNino) { implicit request ⇒ implicit nino ⇒
    handle(enrolmentStore.get(nino), "get enrolment status", nino)
  }

  private def handle[A](f: EitherT[Future, String, A], description: String, nino: NINO)(implicit hc: HeaderCarrier, writes: Writes[A]): Future[Result] = {
    val additionalParams = "apiCorrelationId" -> getApiCorrelationId
    f.fold(
      { e ⇒
        logger.warn(s"Could not $description: $e", nino, additionalParams)
        InternalServerError
      }, { a ⇒
        logger.info(s"$description successful", nino, additionalParams)
        Ok(Json.toJson(a))
      }
    )
  }

  //  private def handleAccountNumber(f: EitherT[Future, String, AccountNumber], description: String, nino: NINO, uri: String)(implicit hc: HeaderCarrier): Future[Result] = {
  //    val additionalParams = "apiCorrelationId" -> getApiCorrelationId
  //    f.leftMap { // why am I using a leftMap?????
  //      case e ⇒
  //        println("####################### leftMap case e")
  //        logger.info(s"Error returned from mongo when trying to obtain account number, error: $e")
  //        getAccountNumberFromNSI(nino, uri).fold(
  //          { e ⇒
  //            println("##################### getAccountNumberFromNSI fold error section")
  //            logger.info("Call to getAccountNumberFromNSI returned error response")
  //            InternalServerError
  //          }, {
  //            accountNumber ⇒
  //              println(s"############# do we get here????? ########### accountNumber is: $accountNumber, from NSI")
  //              Ok(Json.toJson(accountNumber))
  //          }
  //        )
  //    }.fold(
  //      { e ⇒
  //        Ok("Error occurred")
  //      }, { accountNumber ⇒
  //        println(s"############# do we get here????? ########### accountNumber is: $accountNumber, we have the account number in mongo")
  //        Ok(Json.toJson(accountNumber))
  //      }
  //    )
  //  }

  private def handleAccountNumber(f: EitherT[Future, String, AccountNumber], description: String, nino: NINO, uri: String)(implicit hc: HeaderCarrier): Future[Result] = {
    val additionalParams = "apiCorrelationId" -> getApiCorrelationId
    f.leftFlatMap {
      case e ⇒
        println("####################### leftMap case e")
        logger.info(s"Error returned from mongo when trying to obtain account number, error: $e")
        liftF(getAccountNumberFromNSI(nino, uri))
          .fold(
            { e: String ⇒
              println("##################### getAccountNumberFromNSI fold error section")
              logger.info("Call to getAccountNumberFromNSI returned error response")
              "Call to getAccountNumberFromNSI returned error response"
            }, {
              accountNumber ⇒
                println(s"############# do we get here????? ########### accountNumber is: $accountNumber, from NSI")
                accountNumber
            }
          )
    }.fold(
      { e ⇒
        logger.warn("Error occurred when trying to get account number")
        InternalServerError
      }, { accountNumber ⇒
        println(s"############# do we get here????? ########### accountNumber is: $accountNumber, we have the account number in mongo")
        Ok(accountNumber)
      }
    )
  }

  private def getAccountNumberFromNSI(nino: NINO, uri: String)(implicit hc: HeaderCarrier): EitherT[Future, String, AccountNumber] = {
    proxyConnector.getAccount(nino, "help-to-save", getApiCorrelationId(), uri).map { response ⇒
      response match {
        case Some(account) ⇒ {
          logger.info(s"get account from NSI successful", nino)
          setAccountNumber(nino, account.accountNumber).value.onComplete {
            case Success(Right(_)) ⇒ logger.info("Account number was successfully persisted in mongo enrolment store")
            case Success(Left(e))  ⇒ logger.warn(s"Persisting account number in mongo failed, error: $e")
            case Failure(e)        ⇒ logger.warn(s"Could not get user's account number, tried mongo and NSI, error: $e", nino)
          }
        }
        case None ⇒ logger.warn(s"Getting user's account number from NSI failed", nino)
      }
      response.fold[AccountNumber](AccountNumber(None))(ac ⇒ AccountNumber(Some(ac.accountNumber)))
    }
  }

}

object EnrolmentStoreController {

  implicit val unitWrites: Writes[Unit] = new Writes[Unit] {
    override def writes(o: Unit) = JsNull
  }

}
