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

package uk.gov.hmrc.helptosave.services

import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import play.api.mvc.Request
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.BarsConnector
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models.BARSCheck
import uk.gov.hmrc.helptosave.models.bank.{BankDetailsValidationRequest, BankDetailsValidationResult}
import uk.gov.hmrc.helptosave.util.Logging.LoggerOps
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BarsServiceImpl])
trait BarsService {

  type BarsResponseType = Future[Either[String, BankDetailsValidationResult]]

  def validate(
    barsRequest: BankDetailsValidationRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[?]): BarsResponseType

}

@Singleton
class BarsServiceImpl @Inject() (
  barsConnector: BarsConnector,
  metrics: Metrics,
  alerting: PagerDutyAlerting,
  auditor: HTSAuditor
)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
    extends BarsService
    with Logging {

  override def validate(
    barsRequest: BankDetailsValidationRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[?]): BarsResponseType = {
    val timerContext = metrics.barsTimer.time()
    val trackingId   = UUID.randomUUID()
    val nino         = barsRequest.nino
    barsConnector
      .validate(barsRequest, trackingId)
      .flatMap[Either[String, BankDetailsValidationResult]] {
        case Right(response)                                    =>
          val _ = timerContext.stop()
          response.status match {
            case Status.OK  =>
              auditor.sendEvent(BARSCheck(barsRequest, response.json, request.uri), nino)

              (response.json \ "accountNumberIsWellFormatted").asOpt[String] ->
                (response.json \ "sortCodeIsPresentOnEISCD").asOpt[String].map(_.toLowerCase.trim) match {
                case (Some(accountNumberWithSortCodeIsValid), Some(sortCodeIsPresentOnEISCD)) =>
                  val sortCodeExists: Either[String, Boolean] =
                    if sortCodeIsPresentOnEISCD === "yes" then {
                      Right(true)
                    } else if sortCodeIsPresentOnEISCD === "no" then {
                      logger.info("BARS response: bank details were valid but sort code was not present on EISCD", nino)
                      Right(false)
                    } else if sortCodeIsPresentOnEISCD === "error" then {
                      logger.info("BARS response: Sort code check on EISCD returned Error", nino)
                      Right(false)
                    } else {
                      Left(s"Could not parse value for 'sortCodeIsPresentOnEISCD': $sortCodeIsPresentOnEISCD")
                    }

                  val accountNumbersValid: Boolean =
                    if accountNumberWithSortCodeIsValid === "yes" then {
                      true
                    } else if accountNumberWithSortCodeIsValid === "no" then {
                      logger.info("BARS response: bank details were NOT valid", nino)
                      false
                    } else if accountNumberWithSortCodeIsValid === "indeterminate" then {
                      logger.info("BARS response: bank details were marked as indeterminate", nino)
                      true
                    } else {
                      logger.warn("BARS response: Unexpected Return for valid vank details", nino)
                      false
                    }
                  Future(sortCodeExists.map {
                    BankDetailsValidationResult(accountNumbersValid, _)
                  })

                case _ =>
                  logger.warn(
                    s"error parsing the response from bars check, trackingId = $trackingId,  body = ${response.body}"
                  )
                  alerting.alert("error parsing the response json from bars check")
                  Future(Left(s"error parsing the response json from bars check"))
              }
            case other: Int =>
              metrics.barsErrorCounter.inc()
              logger.warn(
                s"unexpected status from bars check, trackingId = $trackingId, status=$other, body = ${response.body}"
              )
              alerting.alert("unexpected status from bars check")
              Future(Left("unexpected status from bars check"))
          }
        case Left(upstreamErrorResponse: UpstreamErrorResponse) =>
          metrics.barsErrorCounter.inc()
          logger.warn(
            s"unexpected error from bars check, trackingId = $trackingId, error=${upstreamErrorResponse.getMessage}"
          )
          alerting.alert("unexpected error from bars check")
          Future(Left("unexpected error from bars check"))
      }
  }
}
