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
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.ExecutionContext

class UCThresholdConnectorProxyActorSpec extends ActorTestSupport("UCThresholdConnectorProxyActorSpec") {
  import system.dispatcher

  val connector = mock[DESConnector]

  val service = mock[HelpToSaveService]

  val actor = system.actorOf(UCThresholdConnectorProxyActor.props(service))

  def mockServiceGetValue(response: Either[String, Double]) =
    (service.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT(toFuture(response)))

  "The UCThresholdConnectorProxyActor" when {

    "asked for the threshold value" must {

      "ask for and return the value from the threshold connector" in {

        mockServiceGetValue(Right(100.0))

        actor ! UCThresholdConnectorProxyActor.GetThresholdValue
        expectMsg(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))
      }

      "ask for and return an error from the threshold connector if an error occurs" in {

        mockServiceGetValue(Left("error occurred"))

        actor ! UCThresholdConnectorProxyActor.GetThresholdValue
        expectMsg(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("error occurred")))
      }

    }
  }
}
