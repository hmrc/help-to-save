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
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class UCThresholdConnectorProxyActor(dESConnector: DESConnector)
    extends Actor with Logging {
  import context.dispatcher

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def getThreshold()(implicit hc: HeaderCarrier): Future[Double] = dESConnector.getThreshold()

  override def receive: Receive = {
    case GetThresholdValue => getThreshold().map(GetThresholdValueResponse).pipeTo(sender())
  }
}

object UCThresholdConnectorProxyActor {

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Double)

  def props(dESConnector: DESConnector): Props =
    Props(new UCThresholdConnectorProxyActor(dESConnector))

}
