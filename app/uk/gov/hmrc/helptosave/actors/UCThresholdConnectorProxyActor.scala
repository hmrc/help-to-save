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

package uk.gov.hmrc.helptosave.actors

import org.apache.pekko.actor.{Actor, Props}
import org.apache.pekko.pattern.pipe
import uk.gov.hmrc.helptosave.actors.UCThresholdConnectorProxyActor.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class UCThresholdConnectorProxyActor(dESConnector: DESConnector, pagerDutyAlerting: PagerDutyAlerting)
    extends Actor with Logging {
  import context.dispatcher

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def getThreshold()(implicit hc: HeaderCarrier): Future[Either[String, Double]] = {
         dESConnector.getThreshold()
           .map(maybeThreshold => {
               maybeThreshold.map(response => {
                   val additionalParams = "DesCorrelationId" -> response.correlationId
                   val result = response.parseJson[UCThreshold]
                   result.fold(
                       { e =>
                           logger.warn(
                               s"Could not parse JSON response from threshold, received 200 (OK): $e, with additionalParams: $additionalParams")
                           pagerDutyAlerting.alert("Could not parse JSON in UC threshold response")
                         },
                       _ =>
                       logger.debug(
                           s"Call to threshold successful, received 200 (OK), with additionalParams: $additionalParams")
                   )
                   result.map(_.thresholdAmount)
                 }).left.map(error => {
                   val additionalParams = "DesCorrelationId" -> error.headers.getOrElse("CorrelationId", "-")
                   logger.warn(
                       s"Call to get threshold unsuccessful. Received unexpected status ${error.statusCode}. " +
                           s"AdditionalParams: $additionalParams")
                   pagerDutyAlerting.alert("Received unexpected http status in response to get UC threshold from DES")
                   s"Received unexpected status ${error.statusCode}"
                 })
             }.flatten)
       }

  override def receive: Receive = {
    case GetThresholdValue => getThreshold().map(GetThresholdValueResponse).pipeTo(sender())
  }
}

object UCThresholdConnectorProxyActor {

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Either[String, Double])

  def props(dESConnector: DESConnector, pagerDutyAlerting: PagerDutyAlerting): Props =
    Props(new UCThresholdConnectorProxyActor(dESConnector, pagerDutyAlerting))

}
