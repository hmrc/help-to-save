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

package uk.gov.hmrc.helptosave.modules

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.actors.{ActorTestSupport, UCThresholdConnectorProxyActor, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class UCThresholdOrchestratorSpec extends ActorTestSupport("UCThresholdOrchestratorSpec") with Eventually {

  implicit val timeout: Timeout = Timeout(10.seconds)

  val connector = mock[DESConnector]
  val service = mock[HelpToSaveService]
  val pagerDutyAlert = mock[PagerDutyAlerting]
  val proxyActor = system.actorOf(UCThresholdConnectorProxyActor.props(connector, pagerDutyAlert))

  def testConfiguration(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |uc-threshold {
       |enabled = $enabled
       |ask-timeout = 10 seconds
       |min-backoff = 1 second
       |max-backoff = 5 seconds
       |number-of-retries-until-initial-wait-doubles = 5
       |update-timezone = UTC
       |update-time = "00:00"
       |update-time-delay = 1 hour
       |}
    """.stripMargin
  ))

  "The UCThresholdOrchestrator" should {
    "start up an instance of the UCThresholdManager correctly" in {
      val threshold = 10.2

      (connector.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *)
        .returning(HttpResponse(500, None))

      (pagerDutyAlert.alert(_: String))
        .expects(*)
        .returning("Received unexpected http status in response to get UC threshold from DES")

      (connector.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *)
        .returning(HttpResponse(200, Some(Json.parse(s"""{ "thresholdAmount" : $threshold }"""))))

      val orchestrator = new UCThresholdOrchestrator(system, pagerDutyAlert, testConfiguration(enabled = true), connector)

      eventually(PatienceConfiguration.Timeout(10.seconds), PatienceConfiguration.Interval(1.second)) {
        val response = (orchestrator.thresholdManager ? UCThresholdManager.GetThresholdValue)
          .mapTo[UCThresholdManager.GetThresholdValueResponse]

        Await.result(response, 1.second).result shouldBe Some(threshold)
      }

      // sleep here to ensure the mock ThresholdStore's storeUCThreshold method
      // definitely gets called since it happens asynchronously
      Thread.sleep(1000L)
    }

    "not start up an instance of the UCThresholdManager if not enabled" in {
      val orchestrator = new UCThresholdOrchestrator(system, pagerDutyAlert, testConfiguration(enabled = false), connector)
      orchestrator.thresholdManager shouldBe ActorRef.noSender
    }

  }
}
