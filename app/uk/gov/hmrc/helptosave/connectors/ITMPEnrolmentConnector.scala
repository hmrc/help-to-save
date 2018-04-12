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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsNull, JsValue, Writes}
import play.mvc.Http.Status.{FORBIDDEN, OK}
import uk.gov.hmrc.helptosave.config.{AppConfig, WSHttp}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps.getCorrelationId
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@ImplementedBy(classOf[ITMPEnrolmentConnectorImpl])
trait ITMPEnrolmentConnector {

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class ITMPEnrolmentConnectorImpl @Inject() (http: WSHttp, metrics: Metrics, pagerDutyAlerting: PagerDutyAlerting)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig)
  extends ITMPEnrolmentConnector with Logging {

  val itmpEnrolmentURL: String = appConfig.baseUrl("itmp-enrolment")

  val body: JsValue = JsNull

  def url(nino: NINO): String = s"$itmpEnrolmentURL/help-to-save/accounts/$nino"

  override def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT({
      val timerContext = metrics.itmpSetFlagTimer.time()

      http.put(url(nino), body, appConfig.desHeaders)(Writes.JsValueWrites, hc.copy(authorization = None), ec)
        .map[Either[String, Unit]] { response ⇒
          val time = timerContext.stop()

          val correlationId = getCorrelationId(hc, appConfig.correlationIdHeaderName)

          response.status match {
            case OK ⇒
              logger.info(s"DES/ITMP HtS flag setting returned status 200 (OK) (round-trip time: ${nanosToPrettyString(time)})", nino, correlationId)
              Right(())

            case FORBIDDEN ⇒
              metrics.itmpSetFlagConflictCounter.inc()
              logger.warn(s"Tried to set ITMP HtS flag even though it was already set, received status 403 (Forbidden) " +
                s"- proceeding as normal  (round-trip time: ${nanosToPrettyString(time)})", nino, correlationId)
              Right(())

            case other ⇒
              metrics.itmpSetFlagErrorCounter.inc()
              pagerDutyAlerting.alert("Received unexpected http status in response to setting ITMP flag")
              Left(s"Received unexpected response status ($other) when trying to set ITMP flag. Body was: ${maskNino(response.body)} " +
                s"(round-trip time: ${nanosToPrettyString(time)})")
          }
        }
        .recover {
          case NonFatal(e) ⇒
            val time = timerContext.stop()
            metrics.itmpSetFlagErrorCounter.inc()
            pagerDutyAlerting.alert("Failed to make call to set ITMP flag")
            Left(s"Encountered unexpected error while trying to set the ITMP flag: ${e.getMessage} (round-trip time: ${nanosToPrettyString(time)})")
        }
    })

}
