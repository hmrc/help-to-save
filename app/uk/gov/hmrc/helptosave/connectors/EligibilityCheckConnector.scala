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

import cats.Show
import cats.data.EitherT
import cats.instances.either._
import cats.instances.option._
import cats.syntax.show._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import uk.gov.hmrc.helptosave.config.{AppConfig, WSHttp}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, UCResponse}
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EligibilityCheckConnectorImpl])
trait EligibilityCheckConnector {
  def isEligible(nino: String, ucResponse: Option[UCResponse])(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]]
}

@Singleton
class EligibilityCheckConnectorImpl @Inject() (http:              WSHttp,
                                               metrics:           Metrics,
                                               pagerDutyAlerting: PagerDutyAlerting)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends EligibilityCheckConnector with Logging {

  val itmpBaseURL: String = appConfig.baseUrl("itmp-eligibility-check")

  implicit val booleanShow: Show[Boolean] = Show.show(if (_) "Y" else "N")

  def url(nino: String, ucResponse: Option[UCResponse]): String = {
    ucResponse match {
      case Some(UCResponse(a, Some(b))) ⇒ s"$itmpBaseURL/help-to-save/eligibility-check/$nino?universalCreditClaimant=${a.show}&withinThreshold=${b.show}"
      case Some(UCResponse(a, None))    ⇒ s"$itmpBaseURL/help-to-save/eligibility-check/$nino?universalCreditClaimant=${a.show}"
      case _                            ⇒ s"$itmpBaseURL/help-to-save/eligibility-check/$nino"
    }
  }

  type EitherStringOr[A] = Either[String, A]

  override def isEligible(nino: String, ucResponse: Option[UCResponse] = None)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]] = // scalastyle:ignore
    EitherT[Future, String, Option[EligibilityCheckResult]](
      {
        val timerContext = metrics.itmpEligibilityCheckTimer.time()

        http.get(url(nino, ucResponse), appConfig.desHeaders)(hc.copy(authorization = None), ec)
          .map { response ⇒
            val time = timerContext.stop()

            logger.info(s"eligibility response body from DES is: ${maskNino(response.body)}", nino, None)

            val res: Option[Either[String, EligibilityCheckResult]] = response.status match {
              case Status.OK ⇒
                val result = response.parseJson[EligibilityCheckResult]
                result.fold({
                  e ⇒
                    metrics.itmpEligibilityCheckErrorCounter.inc()
                    logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e ${timeString(time)}", nino, None)
                    pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
                }, _ ⇒
                  logger.debug(s"Call to check eligibility successful, received 200 (OK) ${timeString(time)}", nino, None)
                )
                Some(result)

              case Status.NOT_FOUND ⇒
                logger.info(s"Retrieved nino has not been found in DES, so user is not receiving Working Tax Credit ${timeString(time)}", nino, None)
                None

              case other ⇒
                logger.warn(s"Call to check eligibility unsuccessful. Received unexpected status $other ${timeString(time)}", nino, None)
                metrics.itmpEligibilityCheckErrorCounter.inc()
                pagerDutyAlerting.alert("Received unexpected http status in response to eligibility check")
                Some(Left(s"Received unexpected status $other"))

            }

            res.traverse[EitherStringOr, EligibilityCheckResult](identity)
          }.recover {
            case e ⇒
              val time = timerContext.stop()
              pagerDutyAlerting.alert("Failed to make call to check eligibility")
              metrics.itmpEligibilityCheckErrorCounter.inc()
              Left(s"Call to check eligibility unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
          }
      })

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"
}
