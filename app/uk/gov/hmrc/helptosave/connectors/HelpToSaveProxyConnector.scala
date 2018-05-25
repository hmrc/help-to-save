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

package uk.gov.hmrc.helptosave.connectors

import java.util.UUID

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import play.mvc.Http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.helptosave.config.{AppConfig, WSHttp}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models.account.{Account, AccountO, NsiAccount}
import uk.gov.hmrc.helptosave.models.{ErrorResponse, NSIUserInfo, UCResponse}
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps._
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Logging.LoggerOps
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveProxyConnectorImpl])
trait HelpToSaveProxyConnector {

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def ucClaimantCheck(nino: String, txnId: UUID, threshold: Double)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UCResponse]

  def getAccount(nino: String, queryString: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO]
}

@Singleton
class HelpToSaveProxyConnectorImpl @Inject() (http:              WSHttp,
                                              metrics:           Metrics,
                                              pagerDutyAlerting: PagerDutyAlerting)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends HelpToSaveProxyConnector with Logging {

  val proxyURL: String = appConfig.baseUrl("help-to-save-proxy")
  implicit val correlationIdHeaderName: String = appConfig.correlationIdHeaderName

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {

    http.post(s"$proxyURL/help-to-save-proxy/create-account", userInfo)
      .recover {
        case e ⇒
          logger.warn(s"unexpected error from proxy during /create-de-account, message=${e.getMessage}", userInfo.nino,
            "apiCorrelationId" -> getApiCorrelationId)
          val errorJson = ErrorResponse("unexpected error from proxy during /create-de-account", s"${e.getMessage}").toJson()
          HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(errorJson))
      }
  }

  override def ucClaimantCheck(nino: String, txnId: UUID, threshold: Double)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UCResponse] = {
    val url = s"$proxyURL/help-to-save-proxy/uc-claimant-check?nino=$nino&transactionId=$txnId&thresholdValue=$threshold"

    EitherT[Future, String, UCResponse](
      http.get(url).map {
        response ⇒

          val correlationId = "apiCorrelationId" -> getApiCorrelationId
          logger.info(s"response body from UniversalCredit check is: ${response.body}", nino, correlationId)

          response.status match {
            case Status.OK ⇒
              val result = response.parseJson[UCResponse]
              result.fold(
                e ⇒ logger.warn(s"Could not parse UniversalCredit response, received 200 (OK), error=$e", nino, correlationId),
                _ ⇒ logger.info(s"Call to check UniversalCredit check is successful, received 200 (OK)", nino, correlationId)
              )
              result

            case other ⇒
              logger.warn(s"Call to check UniversalCredit check unsuccessful. Received unexpected status $other", nino, correlationId)
              Left(s"Received unexpected status($other) from UniversalCredit check")
          }
      }.recover {
        case e ⇒
          Left(s"Call to UniversalCredit check unsuccessful: ${e.getMessage}")
      }
    )
  }

  override def getAccount(nino: String, queryString: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO] = {

    val url = s"$proxyURL/help-to-save-proxy/nsi-services/account?$queryString"
    val timerContext = metrics.getAccountTimer.time()
    EitherT[Future, String, AccountO](
      http.get(url).map[Either[String, AccountO]] {
        response ⇒
          val time = timerContext.stop()
          val correlationId = "correlationId" -> getCorrelationId(queryString)
          response.status match {
            case Status.OK ⇒
              val result = response.parseJson[NsiAccount].map(a ⇒ AccountO(Account(a)))

              result.fold(
                e ⇒ {
                  logger.warn(s"Could not parse getNsiAccount response, received 200 (OK), error=$e, response = ${maskNino(response.body)}", nino, correlationId)
                  metrics.getAccountErrorCounter.inc()
                  pagerDutyAlerting.alert("Could not parse JSON in the getAccount response")
                },
                _ ⇒ logger.info("Call to getNsiAccount successful", nino, correlationId)
              )
              result
            case other ⇒
              logger.warn(s"Call to getNsiAccount unsuccessful. Received unexpected status $other", nino, correlationId)
              metrics.getAccountErrorCounter.inc()
              pagerDutyAlerting.alert("Received unexpected http status in response to getAccount")
              Left(s"Received unexpected status($other) from getNsiAccount call")
          }
      }.recover {
        case NonFatal(e) ⇒
          val time = timerContext.stop()
          metrics.getAccountErrorCounter.inc()
          pagerDutyAlerting.alert("Failed to make call to getAccount")
          Left(s"Call to getNsiAccount unsuccessful: ${e.getMessage}")
      }
    )
  }

  private def getCorrelationId(queryString: String): String = {
    queryString.split("&")
      .find(p ⇒ p.contains("correlationId"))
      .map(p ⇒ p.split("=")(1))
      .getOrElse("-")
  }
}
