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

package uk.gov.hmrc.helptosave.modules

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import org.mockito.ArgumentMatchersSugar.*
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.actors.{ActorTestSupport, UCThresholdConnectorProxyActor, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class UCThresholdOrchestratorSpec extends ActorTestSupport("UCThresholdOrchestratorSpec") with Eventually {
  implicit val timeout: Timeout = Timeout(10.seconds)

  val connector: DESConnector = mock[DESConnector]
  val service: HelpToSaveService = mock[HelpToSaveService]
  val pagerDutyAlert: PagerDutyAlerting = mock[PagerDutyAlerting]
  val proxyActor: ActorRef = system.actorOf(UCThresholdConnectorProxyActor.props(connector, pagerDutyAlert))

  val testConfiguration: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        |uc-threshold {
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
      connector
        .getThreshold()(*, *)
        .returns(Future.successful(
          Right(HttpResponse(200, Json.parse(s"""{ "thresholdAmount" : $threshold }"""), Map[String, Seq[String]]()))
        ))

      val orchestrator = new UCThresholdOrchestrator(system, pagerDutyAlert, testConfiguration, connector)

      eventually(PatienceConfiguration.Timeout(10.seconds), PatienceConfiguration.Interval(1.second)) {
        val response = (orchestrator.thresholdManager ? UCThresholdManager.GetThresholdValue)
          .mapTo[UCThresholdManager.GetThresholdValueResponse]

        Await.result(response, 1.second).result shouldBe Some(threshold)
      }

      // sleep here to ensure the mock ThresholdStore's storeUCThreshold method
      // definitely gets called since it happens asynchronously
      Thread.sleep(1000L)
    }
    "instance of the UCThresholdManager doesn't start" in {
      connector
        .getThreshold()(*, *)
        .returns(Future.successful(Left(UpstreamErrorResponse("error occurred",500))))

      val response = await(connector.getThreshold())
      response shouldBe Left(UpstreamErrorResponse("error occurred",500))

      pagerDutyAlert
        .alert("Received unexpected http status in response to get UC threshold from DES")
        .doesNothing()
    }
  }
}
