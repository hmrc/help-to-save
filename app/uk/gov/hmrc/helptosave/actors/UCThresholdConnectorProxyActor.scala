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

package uk.gov.hmrc.helptosave.actors

import akka.pattern.pipe
import akka.actor.{Actor, Props}
import uk.gov.hmrc.helptosave.actors.UCThresholdConnectorProxyActor.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.connectors.UCThresholdConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UCThresholdConnectorProxyActor(thresholdConnector: UCThresholdConnector) extends Actor {
  import context.dispatcher

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def getValue()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Double]] = thresholdConnector.getThreshold().value

  override def receive: Receive = {
    case GetThresholdValue ⇒ getValue().map(GetThresholdValueResponse) pipeTo sender
  }
}

object UCThresholdConnectorProxyActor {

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Either[String, Double])

  def props(thresholdConnector: UCThresholdConnector): Props =
    Props(new UCThresholdConnectorProxyActor(thresholdConnector))

}
