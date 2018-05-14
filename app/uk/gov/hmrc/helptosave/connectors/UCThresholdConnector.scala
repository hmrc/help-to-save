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
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import uk.gov.hmrc.helptosave.config.{AppConfig, WSHttp}
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, PagerDutyAlerting, Result}
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UCThresholdConnectorImpl])
trait UCThresholdConnector {

  def getThreshold()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Double]

}

@Singleton
class UCThresholdConnectorImpl @Inject() (http:              WSHttp,
                                          pagerDutyAlerting: PagerDutyAlerting)(implicit appConfig: AppConfig, logMessageTransformer: LogMessageTransformer)
  extends UCThresholdConnector with DESConnector with Logging {

  val itmpThresholdURL: String = s"${appConfig.baseUrl("itmp-threshold")}/universal-credits/threshold-amount"

  def getThreshold()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Double] =
    EitherT[Future, String, Double](
      {
        http.get(itmpThresholdURL, appConfig.desHeaders)(hc.copy(authorization = None), ec)
          .map { response ⇒

            val additionalParams = "DesCorrelationId" -> desCorrelationId(response)

            logger.info(s"threshold response from DES is: ${response.body}")

            response.status match {
              case Status.OK ⇒
                val result = response.parseJson[UCThreshold]
                result.fold({
                  e ⇒
                    logger.warn(s"Could not parse JSON response from threshold, received 200 (OK): $e", "-", additionalParams)
                    pagerDutyAlerting.alert("Could not parse JSON in UC threshold response")
                }, _ ⇒
                  logger.debug(s"Call to threshold successful, received 200 (OK)", "-", additionalParams)
                )
                result.map(_.thresholdAmount)

              case other ⇒
                logger.warn(s"Call to get threshold unsuccessful. Received unexpected status $other. " +
                  s"Body was: ${response.body}", "-", additionalParams)
                pagerDutyAlerting.alert("Received unexpected http status in response to get UC threshold from DES")
                Left(s"Received unexpected status $other")
            }
          }.recover {
            case e ⇒
              pagerDutyAlerting.alert("Failed to make call to get UC threshold from DES")
              Left(s"Call to get threshold unsuccessful: ${e.getMessage}")
          }
      })
}
