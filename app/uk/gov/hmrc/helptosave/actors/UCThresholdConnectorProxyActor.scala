/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.pattern.pipe
import akka.actor.{Actor, Props}
import cats.syntax.either._
import play.api.http.Status
import uk.gov.hmrc.helptosave.actors.UCThresholdConnectorProxyActor.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.helptosave.util.HttpResponseOps._

import scala.concurrent.{ExecutionContext, Future}

class UCThresholdConnectorProxyActor(dESConnector: DESConnector, pagerDutyAlerting: PagerDutyAlerting) extends Actor with Logging {
  import context.dispatcher

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def getThreshold()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Double]] =
    dESConnector.getThreshold().map[Either[String, Double]] { response ⇒

      val additionalParams = "DesCorrelationId" -> response.desCorrelationId

      logger.info(s"threshold response from DES is: ${response.body}")

      response.status match {
        case Status.OK ⇒
          val result = response.parseJson[UCThreshold]
          result.fold({
            e ⇒
              logger.warn(s"Could not parse JSON response from threshold, received 200 (OK): $e, with additionalParams: $additionalParams")
              pagerDutyAlerting.alert("Could not parse JSON in UC threshold response")
          }, _ ⇒
            logger.debug(s"Call to threshold successful, received 200 (OK), with additionalParams: $additionalParams")
          )
          result.map(_.thresholdAmount)

        case other ⇒
          logger.warn(s"Call to get threshold unsuccessful. Received unexpected status $other. " +
            s"Body was: ${response.body}, with additionalParams: $additionalParams")
          pagerDutyAlerting.alert("Received unexpected http status in response to get UC threshold from DES")
          Left(s"Received unexpected status $other")
      }
    }.recover {
      case e ⇒
        pagerDutyAlerting.alert("Failed to make call to get UC threshold from DES")
        Left(s"Call to get threshold unsuccessful: ${e.getMessage}")
    }

  override def receive: Receive = {
    case GetThresholdValue ⇒ getThreshold().map(GetThresholdValueResponse) pipeTo sender
  }
}

object UCThresholdConnectorProxyActor {

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Either[String, Double])

  def props(dESConnector: DESConnector, pagerDutyAlerting: PagerDutyAlerting): Props =
    Props(new UCThresholdConnectorProxyActor(dESConnector, pagerDutyAlerting))

}
