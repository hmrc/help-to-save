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

import akka.pattern.ask
import akka.util.Timeout
import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{ActorTestSupport, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.UCThresholdConnector
import uk.gov.hmrc.helptosave.repo.ThresholdStore
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class UCThresholdOrchestratorSpec extends ActorTestSupport("UCThresholdOrchestratorSpec") with Eventually {

  import system.dispatcher

  implicit val timeout: Timeout = Timeout(10.seconds)

  val connector = mock[UCThresholdConnector]
  val store = mock[ThresholdStore]
  val pagerDutyAlert = mock[PagerDutyAlerting]

  val testConfiguration = Configuration(ConfigFactory.parseString(
    """
      |uc-threshold {
      |ask-timeout = 10 seconds
      |min-backoff = 1 second
      |max-backoff = 5 seconds
      |number-of-retries-until-initial-wait-doubles = 5
      |}
    """.stripMargin
  ))

  "The UCThresholdOrchestrator" should {
    "start up an instance of the UCThresholdManager correctly" in {
      val threshold = 10.2

      (connector.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *)
        .returning(EitherT.fromEither[Future](Left[String, Double]("")))

      (store.getUCThreshold()(_: ExecutionContext))
        .expects(*)
        .returning(EitherT.fromEither[Future](Left[String, Option[Double]]("")))

      (pagerDutyAlert.alert(_: String))
        .expects(*)
        .returning(())

      (connector.getThreshold()(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *)
        .returning(EitherT.fromEither[Future](Right[String, Double](threshold)))

      (store.storeUCThreshold(_: Double)(_: ExecutionContext))
        .expects(threshold, *)
        .returning(EitherT.fromEither[Future](Right[String, Unit](())))

      val orchestrator = new UCThresholdOrchestrator(system, pagerDutyAlert, testConfiguration, connector, store)

      val response = (orchestrator.thresholdHandler ? UCThresholdManager.GetThresholdValue)
        .mapTo[UCThresholdManager.GetThresholdValueResponse]

      Await.result(response, 10.seconds).result shouldBe threshold
      // sleep here to ensure the mock ThresholdStore's storeUCThreshold method
      // definitely gets called since it happens asynchronously
      Thread.sleep(1000L)
    }
  }
}
