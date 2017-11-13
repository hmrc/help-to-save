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
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

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

  val headers: Map[String, String] = Map(
    "Environment" → getString("microservice.services.itmp-eligibility-check.environment"),
    "Authorization" → s"Bearer ${getString("microservice.services.itmp-eligibility-check.token")}"
  )

  override def isEligible(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] =
    EitherT[Future, String, EligibilityCheckResult](
      {
        val timerContext = metrics.itmpEligibilityCheckTimer.time()

        http.get(url(nino), headers)
          .map { response ⇒
            val time = timerContext.stop()

            val result = response.parseJson[EligibilityCheckResult]
            result.fold({
              e ⇒
                metrics.itmpEligibilityCheckErrorCounter.inc()
                logger.warn(s"Call to check eligibility unsuccessful: $e. Received status ${response.status} ${timeString(time)}", nino)
            }, _ ⇒
              logger.info(s"Call to check eligibility successful, received 200 (OK) ${timeString(time)}", nino)
            )
            result
          }
          .recover {
            case e ⇒
              val time = timerContext.stop()

              metrics.itmpEligibilityCheckErrorCounter.inc()
              Left(s"Call to check eligibility unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
          }
      })

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"
}
