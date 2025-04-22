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

import org.apache.pekko.actor.ActorRef
import org.mockito.ArgumentMatchers.any
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.util.*
import uk.gov.hmrc.helptosave.utils.MockPagerDuty
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import org.mockito.Mockito.{doNothing, when}

import scala.concurrent.Future

class UCThresholdConnectorProxyActorSpec
    extends ActorTestSupport("UCThresholdConnectorProxyActorSpec")
    with MockitoSugar
    with MockPagerDuty
    with EitherValues {
  val returnHeaders: Map[String, Seq[String]] = Map[String, Seq[String]]()
  val connector: DESConnector                 = mock[DESConnector]

  val actor: ActorRef = system.actorOf(UCThresholdConnectorProxyActor.props(connector, mockPagerDuty))

  def mockConnectorGetValue(
    response: HttpResponse
  ): OngoingStubbing[Future[Either[UpstreamErrorResponse, HttpResponse]]] =
    when(connector.getThreshold()(any(), any())).thenReturn(toFuture(Right(response)))

  "The UCThresholdConnectorProxyActor" when {

    "asked for the threshold value" must {

      "ask for and return the value from the threshold connector" in {
        when(connector.getThreshold()(any(), any())).thenReturn(
          toFuture(Right(HttpResponse(200, Json.parse("""{"thresholdAmount" : 100.0}"""), returnHeaders)))
        )
      }

      "ask for and return is successful but invalid threshold amount" in {
        when(connector.getThreshold()(any(), any())).thenReturn(
          toFuture(Right(HttpResponse(200, JsString(""), returnHeaders)))
        )

        doNothing()
          .when(mockPagerDuty)
          .alert("Could not parse JSON in UC threshold response")

        actor ! UCThresholdConnectorProxyActor.GetThresholdValue
        expectMsg(
          UCThresholdConnectorProxyActor.GetThresholdValueResponse(
            Left("Could not parse http response JSON: : [error.expected.jsobject]. Response body was \"\"")
          )
        )
      }

      "ask for and return an error from the threshold connector if an error occurs" in {
        when(connector.getThreshold()(any(), any()))
          .thenReturn(toFuture(Left(UpstreamErrorResponse("error occurred", 500))))

        doNothing()
          .when(mockPagerDuty)
          .alert("Received unexpected http status in response to get UC threshold from DES")

        actor ! UCThresholdConnectorProxyActor.GetThresholdValue
        expectMsg(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("Received unexpected status 500")))
      }

    }
  }
}
