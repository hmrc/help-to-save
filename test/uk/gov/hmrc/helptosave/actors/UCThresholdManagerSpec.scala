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
import akka.actor.{Actor, ActorRef}
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

    val mongoProxy = TestProbe()

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
      system.actorOf(UCThresholdManager.props(connectorProxy.ref, mongoProxy.ref, new TestPagerDutyAlerting(self), time.scheduler, config))

    def askForThresholdValue(thresholdActor: ActorRef): Future[Double] =
      (thresholdActor ? UCThresholdManager.GetThresholdValue)
        .mapTo[UCThresholdManager.GetThresholdValueResponse]
        .map(_.result)
  }

  "The ThresholdActor" when {

    "started" must {

      "ask for the threshold value from the connector and return the value when successful" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
        connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Right(12.0)))
        mongoProxy.expectMsg(ThresholdMongoProxy.StoreThresholdValue(12.0))
        mongoProxy.reply(ThresholdMongoProxy.StoreThresholdValueResponse(Right(12.0)))

        await(askForThresholdValue(actor)) shouldBe 12.0

      }

      "ask for the threshold value from the connector and ask mongo if the call to DES is unsuccessful and return the value" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
        connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
        mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
        mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Right(Some(100.0))))

        await(askForThresholdValue(actor)) shouldBe 100.0
      }

      "ask for the threshold value from the connector and when the call to DES is unsuccessful, ask mongo and return None, retry twice and then " +
        "return the threshold from DES and then make sure the retry has stopped" in new TestApparatus {
          val actor = newThresholdActor()

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
          mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
          mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Right(None)))

          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not initialise threshold value"))

          // make sure actor has actually scheduled the retry before advancing time - send it
          // an `Identify` message and wait for it to respond
          awaitActorReady(actor)

          // make sure retry doesn't happen before it's supposed to
          time.advance(1.second - 1.milli)
          mongoProxy.expectNoMsg(1.second)

          time.advance(1.milli)

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
          mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
          mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Right(None)))
          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not initialise threshold value"))

          awaitActorReady(actor)
          // make sure retry is done again
          time.advance(10.second)

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Right(12.0)))

          // make sure there are no retries after success
          awaitActorReady(actor)
          time.advance(10.seconds)

          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(12.0))
        }

      "ask for the threshold value from the connector and when the call to DES is unsuccessful, ask mongo and return an error, retry then" +
        "return the threshold from Mongo and then make sure the retry has stopped" in new TestApparatus {
          val actor = newThresholdActor()

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
          mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
          mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Left("No threshold found in Mongo")))
          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not initialise threshold value"))

          // make sure actor has actually scheduled the retry before advancing time - send it
          // an `Identify` message and wait for it to respond
          awaitActorReady(actor)

          // make sure retry doesn't happen before it's supposed to
          time.advance(1.second - 1.milli)
          mongoProxy.expectNoMsg(1.second)

          time.advance(1.milli)

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
          mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
          mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Left("No threshold found in Mongo")))
          expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not initialise threshold value"))

          awaitActorReady(actor)
          // make sure retry is done again
          time.advance(10.second)

          connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
          connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Left("No threshold found in DES")))
          mongoProxy.expectMsg(ThresholdMongoProxy.GetThresholdValue)
          mongoProxy.reply(ThresholdMongoProxy.GetThresholdValueResponse(Right(Some(12.0))))

          // make sure there are no retries after success
          awaitActorReady(actor)
          time.advance(10.seconds)

          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(12.0))
        }

      "ask for the threshold value from the connector and return an error when there is no response" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)

        actor ! UCThresholdManager.GetThresholdValue
        expectNoMsg()
      }

      "ask for the threshold and retry storing threshold value when mongo is down and stop retrying when mongo is up" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(ThresholdConnectorProxy.GetThresholdValue)
        connectorProxy.reply(ThresholdConnectorProxy.GetThresholdValueResponse(Right(12.0)))

        mongoProxy.expectMsg(ThresholdMongoProxy.StoreThresholdValue(12.0))
        mongoProxy.reply(ThresholdMongoProxy.StoreThresholdValueResponse(Left("Mongo unavailable, unable to store threshold value")))

        // make sure actor has actually scheduled the retry before advancing time - send it
        // an `Identify` message and wait for it to respond
        awaitActorReady(actor)

        // make sure retry doesn't happen before it's supposed to
        time.advance(1.second - 1.milli)
        mongoProxy.expectNoMsg(1.second)

        time.advance(1.milli)

        mongoProxy.expectMsg(ThresholdMongoProxy.StoreThresholdValue(12.0))
        mongoProxy.reply(ThresholdMongoProxy.StoreThresholdValueResponse(Left("Mongo unavailable, unable to store threshold value")))

        awaitActorReady(actor)
        // make sure retry is done again
        time.advance(10.second)

        mongoProxy.expectMsg(ThresholdMongoProxy.StoreThresholdValue(12.0))
        mongoProxy.reply(ThresholdMongoProxy.StoreThresholdValueResponse(Left("Mongo unavailable, unable to store threshold value")))

        awaitActorReady(actor)
        time.advance(10.second)

        mongoProxy.expectMsg(ThresholdMongoProxy.StoreThresholdValue(12.0))
        mongoProxy.reply(ThresholdMongoProxy.StoreThresholdValueResponse(Right(12.0)))

        // make sure there are no retries after success
        awaitActorReady(actor)
        time.advance(10.seconds)

        mongoProxy.expectNoMsg(1.second)

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
