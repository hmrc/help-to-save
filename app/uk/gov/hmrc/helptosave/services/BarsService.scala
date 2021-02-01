/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.services

import java.util.UUID

import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import play.api.mvc.Request
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.BarsConnector
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models.{BARSCheck, BankDetailsValidationResult, BankDetailsValidationRequest}
import uk.gov.hmrc.helptosave.util.Logging.LoggerOps
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BarsServiceImpl])
trait BarsService {

  type BarsResponseType = Future[Either[String, BankDetailsValidationResult]]

  def validate(barsRequest: BankDetailsValidationRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): BarsResponseType

}

@Singleton
class BarsServiceImpl @Inject() (barsConnector: BarsConnector,
                                 metrics:       Metrics,
                                 alerting:      PagerDutyAlerting,
                                 auditor:       HTSAuditor)(implicit transformer: LogMessageTransformer, appConfig: AppConfig) extends BarsService with Logging {

  override def validate(barsRequest: BankDetailsValidationRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): BarsResponseType = {
    val timerContext = metrics.barsTimer.time()
    val trackingId = UUID.randomUUID()
    val nino = barsRequest.nino
    barsConnector.validate(barsRequest, trackingId).map[Either[String, BankDetailsValidationResult]] {
      response ⇒
        val _ = timerContext.stop()
        response.status match {
          case Status.OK ⇒
            auditor.sendEvent(BARSCheck(barsRequest, response.json, request.uri), nino)

            (response.json \ "accountNumberWithSortCodeIsValid").asOpt[Boolean] →
              (response.json \ "sortCodeIsPresentOnEISCD").asOpt[String].map(_.toLowerCase.trim) match {
                case (Some(accountNumberWithSortCodeIsValid), Some(sortCodeIsPresentOnEISCD)) ⇒
                  val sortCodeExists: Either[String, Boolean] =
                    if (sortCodeIsPresentOnEISCD === "yes") {
                      Right(true)
                    } else if (sortCodeIsPresentOnEISCD === "no") {
                      logger.info("BARS response: bank details were valid but sort code was not present on EISCD", nino)
                      Right(false)
                    } else {
                      Left(s"Could not parse value for 'sortCodeIsPresentOnEISCD': $sortCodeIsPresentOnEISCD")
                    }

                  sortCodeExists.map{ BankDetailsValidationResult(accountNumberWithSortCodeIsValid, _) }
                case _ ⇒
                  logger.warn(s"error parsing the response from bars check, trackingId = $trackingId,  body = ${response.body}")
                  alerting.alert("error parsing the response json from bars check")
                  Left(s"error parsing the response json from bars check")
              }
          case other: Int ⇒
            metrics.barsErrorCounter.inc()
            logger.warn(s"unexpected status from bars check, trackingId = $trackingId, status=$other, body = ${response.body}")
            alerting.alert("unexpected status from bars check")
            Left("unexpected status from bars check")
        }
    }.recover {
      case e ⇒
        metrics.barsErrorCounter.inc()
        logger.warn(s"unexpected error from bars check, trackingId = $trackingId, error=${e.getMessage}")
        alerting.alert("unexpected error from bars check")
        Left("unexpected error from bars check")
    }
  }
}
