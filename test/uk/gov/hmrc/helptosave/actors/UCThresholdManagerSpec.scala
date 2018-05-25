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

    val pagerDutyAlertListener = TestProbe()

    def newThresholdActor(): ActorRef =
      system.actorOf(UCThresholdManager.props(connectorProxy.ref, new TestPagerDutyAlerting(pagerDutyAlertListener.ref), time.scheduler, config))

    def askForThresholdValue(thresholdActor: ActorRef): Future[Option[Double]] =
      (thresholdActor ? UCThresholdManager.GetThresholdValue)
        .mapTo[UCThresholdManager.GetThresholdValueResponse]
        .map(_.result)
  }

  "The ThresholdActor" when {

    "started" must {

      "ask DES for the threshold value and store the value in memory when successful" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

        // wait until actor replies replies to Identify message to make sure it has changed state
        // before asking for threshold value
        awaitActorReady(actor)

        await(askForThresholdValue(actor)) shouldBe Some(12.0)
      }

      "make multiple attempts to retrieve the threshold from DES until successful (this is done under an exponential " +
        "backoff strategy) and fire pager duty alerts on each failure" in new TestApparatus {
          val actor = newThresholdActor()

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

          // make sure actor has actually scheduled the retry before advancing time - send it
          // an `Identify` message and wait for it to respond
          awaitActorReady(actor)

          // make sure retry doesn't happen before it's supposed to
          time.advance(1.second - 1.milli)
          connectorProxy.expectNoMsg(1.second)

          time.advance(1.milli)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

          awaitActorReady(actor)
          // make sure retry is done again
          time.advance(10.second)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // make sure there are no retries after success
          awaitActorReady(actor)
          time.advance(10.seconds)

          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(12.0)))
        }

    }

    "handling requests to get the threshold value" must {

      "ask DES for the initial threshold value and return it when successful" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
        pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        //return a Right successful case with value from DES
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

        time.advance(10.seconds)

        // make sure there are no retries after success
        connectorProxy.expectNoMsg()
      }

      "ask DES for the initial threshold value and return None when not successful" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
        pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        //return a Left "error" case from DES
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

        //check None is returned
        expectMsg(UCThresholdManager.GetThresholdValueResponse(None))
      }

      "update the state with the new value when the threshold value received from DES differs from that held locally" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //First call to DES returns the value successfully
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))
        //Second call to DES also returns a different value successfully to mimick an updated threshold value
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(110.0)))

        //make sure the updated value is returned
        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(110.0)))
      }

      "not update the state with the new value when the threshold value received from DES equals from that held locally" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //First call to DES returns the value successfully
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))
        //Second call to DES also returns the same value successfully
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

        //make sure the same value is returned
        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))
      }

      "return None when error responses are returned from two different actors making calls to DES" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //two calls are made to DES, both returning an error
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

        pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))
        //check that None is returned
        time.advance(5.seconds)
        expectMsg(UCThresholdManager.GetThresholdValueResponse(None))
      }

      "return the threshold when the first call to DES is successful but an error is returned from the second call" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //two calls are made to DES, the first successfully returns the value, the second returns an error
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

        //check that the value is still returned despite an error being returned by the second call
        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))
      }

      "stop the retries when a request has successfully returned the threshold from DES" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //first call to DES returns successfully
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

        //second call to DES returns an error
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

        //advance time by 10 seconds and then check there are no expected messages as this means no retries have happened
        time.advance(15.seconds)
        expectNoMsg
      }

      "be in ready state when a request has successfully returned the threshold from DES" in new TestApparatus {
        val actor = newThresholdActor()

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender1: ActorRef = connectorProxy.sender()

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        val sender2: ActorRef = connectorProxy.sender()

        //first call to DES returns an error
        connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

        pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain initial UC threshold value from DES"))

        //second call to DES returns the value
        connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

        // make sure we're not getting the threshold value from DES now
        actor ! UCThresholdManager.GetThresholdValue
        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))
        connectorProxy.expectNoMsg()

        // make sure retries have been cancelled
        time.advance(10.seconds)
        expectNoMsg
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
