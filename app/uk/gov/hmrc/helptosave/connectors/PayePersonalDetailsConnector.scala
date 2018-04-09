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
import play.api.http.Status
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.models.PayePersonalDetails
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{Logging, NINO, LogMessageTransformer, PagerDutyAlerting, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PayePersonalDetailsConnectorImpl])
trait PayePersonalDetailsConnector {

  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayePersonalDetails]

}

@Singleton
class PayePersonalDetailsConnectorImpl @Inject() (http:              WSHttp,
                                                  metrics:           Metrics,
                                                  pagerDutyAlerting: PagerDutyAlerting)(implicit transformer: LogMessageTransformer)
  extends PayePersonalDetailsConnector with ServicesConfig with DESConnector with Logging {

  val payeURL: String = baseUrl("paye-personal-details")

  val ppdHeaders: Map[String, String] = Map(
    "Environment" → getString("microservice.services.paye-personal-details.environment"),
    "Authorization" → s"Bearer ${getString("microservice.services.paye-personal-details.token")}",
    "Originator-Id" → getString("microservice.services.paye-personal-details.originatorId")
  )

  def payePersonalDetailsUrl(nino: String): String = s"$payeURL/pay-as-you-earn/02.00.00/individuals/$nino"

  override def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayePersonalDetails] =
    EitherT[Future, String, PayePersonalDetails](
      {
        val timerContext = metrics.payePersonalDetailsTimer.time()

        http.get(payePersonalDetailsUrl(nino), ppdHeaders)(hc.copy(authorization = None), ec)
          .map { response ⇒
            val time = timerContext.stop()

            response.status match {
              case Status.OK ⇒
                val result = response.parseJson[PayePersonalDetails]
                result.fold({
                  e ⇒
                    metrics.payePersonalDetailsErrorCounter.inc()
                    logger.warn(s"Could not parse JSON response from paye-personal-details, received 200 (OK): $e ${timeString(time)}", nino, None)
                    pagerDutyAlerting.alert("Could not parse JSON in the paye-personal-details response")
                }, _ ⇒
                  logger.debug(s"Call to check paye-personal-details successful, received 200 (OK) ${timeString(time)}", nino, None)
                )
                result

              case other ⇒
                logger.warn(s"Call to paye-personal-details unsuccessful. Received unexpected status $other ${timeString(time)}", nino, None)
                metrics.payePersonalDetailsErrorCounter.inc()
                pagerDutyAlerting.alert("Received unexpected http status in response to paye-personal-details")
                Left(s"Received unexpected status $other")

            }

          }.recover {
            case e ⇒
              val time = timerContext.stop()
              pagerDutyAlerting.alert("Failed to make call to paye-personal-details")
              metrics.payePersonalDetailsErrorCounter.inc()
              Left(s"Call to paye-personal-details unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
          }
      })

  def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"
}
