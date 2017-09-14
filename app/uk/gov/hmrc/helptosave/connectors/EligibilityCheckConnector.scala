/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.models.EligibilityCheckResult
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.{Logging, Result}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EligibilityCheckConnectorImpl])
trait EligibilityCheckConnector {
  def isEligible(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult]
}

@Singleton
class EligibilityCheckConnectorImpl @Inject() (http: WSHttp, metrics: Metrics) extends EligibilityCheckConnector with ServicesConfig with Logging {

  val itmpBaseURL: String = baseUrl("itmp-eligibility-check")

  def url(nino: String): String =
    s"$itmpBaseURL/help-to-save/eligibility-check/$nino"

  override def isEligible(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] =
    EitherT[Future, String, EligibilityCheckResult](
      {
        val timerContext = metrics.itmpEligibilityCheckTimer.time()

        http.get(url(nino))
          .map { response ⇒
            val time = timerContext.stop()
            logger.info(s"Received response from ITMP eligibility check in ${nanosToPrettyString(time)}")
            val result = response.parseJson[EligibilityCheckResult]
            result.fold(_ ⇒ metrics.itmpEligibilityCheckErrorCounter.inc(), _ ⇒ ())
            result
          }
          .recover {
            case e ⇒
              val time = timerContext.stop()
              metrics.itmpEligibilityCheckErrorCounter.inc()
              Left(s"Error encountered when checking eligibility: ${e.getMessage} (time: ${nanosToPrettyString(time)})")
          }
      })
}
