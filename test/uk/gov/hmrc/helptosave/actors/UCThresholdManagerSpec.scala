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

import akka.pattern.ask
import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.ConfigFactory
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting

import scala.concurrent.duration._
import scala.concurrent.Future

class UCThresholdManagerSpec extends ActorTestSupport("UCThresholdManagerSpec") {

  import uk.gov.hmrc.helptosave.actors.UCThresholdManagerSpec.TestPagerDutyAlerting

  implicit val timeout: Timeout = Timeout(10.seconds)

  class TestApparatus {
    val connectorProxy = TestProbe()

    val time = new VirtualTime()

    val config = ConfigFactory.parseString(
      """
        |uc-threshold {
        |ask-timeout = 10 seconds
        |min-backoff = 1 second
        |max-backoff = 5 seconds
        |number-of-retries-until-initial-wait-doubles = 5
        |}
      """.stripMargin
    )

    def newThresholdActor(): ActorRef =
      system.actorOf(UCThresholdManager.props(connectorProxy.ref, new TestPagerDutyAlerting(self), time.scheduler, config))

    def askForThresholdValue(thresholdActor: ActorRef): Future[Double] =
      (thresholdActor ? UCThresholdManager.GetThresholdValue)
        .mapTo[UCThresholdManager.GetThresholdValueResponse]
        .map(_.result)
  }

  "The ThresholdActor" when {

    "started" must {

      "ask for the threshold value from the connector and store the value in memory when successful" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

        // wait until actor replies replies to Identify message to make sure it has changed state
        // before asking for threshold value
        awaitActorReady(actor)

        await(askForThresholdValue(actor)) shouldBe 12.0

      }

      "make multiple attempts to retrieve the threshold from DES until successful (this is done under an exponential " +
        "backoff strategy) and fire pager duty alerts on each failure" in new TestApparatus {
          val actor = newThresholdActor()

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

          // make sure actor has actually scheduled the retry before advancing time - send it
          // an `Identify` message and wait for it to respond
          awaitActorReady(actor)

          // make sure retry doesn't happen before it's supposed to
          time.advance(1.second - 1.milli)
          connectorProxy.expectNoMsg(1.second)

          time.advance(1.milli)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

          awaitActorReady(actor)
          // make sure retry is done again
          time.advance(10.second)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // make sure there are no retries after success
          awaitActorReady(actor)
          time.advance(10.seconds)

          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(12.0))
        }

    }

  }

}

object UCThresholdManagerSpec {
  class TestPagerDutyAlerting(reportTo: ActorRef) extends PagerDutyAlerting {

    override def alert(message: String): Unit = reportTo ! TestPagerDutyAlerting.PagerDutyAlert(message)
  }

  object TestPagerDutyAlerting {
    case class PagerDutyAlert(message: String)
  }
}
