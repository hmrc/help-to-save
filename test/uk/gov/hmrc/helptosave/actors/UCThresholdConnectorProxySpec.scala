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

import cats.data.EitherT
import cats.instances.future._
import uk.gov.hmrc.helptosave.connectors.UCThresholdConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UCThresholdConnectorProxySpec extends ActorTestSupport("UCThresholdConnectorProxySpec") {
  import system.dispatcher

  val connector = mock[UCThresholdConnector]

  val actor = system.actorOf(UCThresholdConnectorProxy.props(connector))

  def mockConnectorGetValue(result: Either[String, Double]) =
    (connector.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  "The UCThresholdConnectorProxy" when {

    "asked for the threshold value" must {

      "ask for and return the value from the threshold connector" in {

        mockConnectorGetValue(Right(100.0))

        actor ! UCThresholdConnectorProxy.GetThresholdValue
        expectMsg(UCThresholdConnectorProxy.GetThresholdValueResponse(Right(100.0)))
      }

      "ask for and return an error from the threshold connector if an error occurs" in {

        mockConnectorGetValue(Left("error occurred"))

        actor ! UCThresholdConnectorProxy.GetThresholdValue
        expectMsg(UCThresholdConnectorProxy.GetThresholdValueResponse(Left("error occurred")))
      }

    }
  }
}