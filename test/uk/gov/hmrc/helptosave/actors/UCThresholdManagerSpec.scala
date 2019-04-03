/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time._
import java.time.format.DateTimeFormatter

import akka.pattern.ask
import akka.actor.{ActorRef, Cancellable}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.miguno.akka.testing.{MockScheduler, VirtualTime}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.helptosave.actors.UCThresholdManagerSpec.TestScheduler.JobScheduledOnce
import uk.gov.hmrc.helptosave.actors.UCThresholdManagerSpec.{TestScheduler, TestTimeCalculator}
import uk.gov.hmrc.helptosave.actors.UCThresholdManagerSpec.TestTimeCalculator.{IsTimeInBetweenRequest, IsTimeInBetweenResponse, TimeUntilRequest, TimeUntilResponse}
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UCThresholdManagerSpec extends ActorTestSupport("UCThresholdManagerSpec") with Eventually {

  import uk.gov.hmrc.helptosave.actors.UCThresholdManagerSpec.TestPagerDutyAlerting

  implicit val timeout: Timeout = Timeout(10.seconds)

  val updateWindowStartTime = LocalTime.MIDNIGHT

  val updateDelay = 30.minutes

  val updateWindowEndTime = updateWindowStartTime.plusSeconds(updateDelay.toSeconds)

  class TestApparatus {
    val connectorProxy = TestProbe()
    val timeCalculatorListener = TestProbe()
    val schedulerListener = TestProbe()
    val pagerDutyAlertListener = TestProbe()

    val testTimeCalculator = new TestTimeCalculator(timeCalculatorListener.ref)

    val time = new VirtualTime() {
      override val scheduler = new TestScheduler(schedulerListener.ref, this)
    }

    val config = ConfigFactory.parseString(
      s"""
         |uc-threshold {
         |  ask-timeout = 1 minute
         |  min-backoff = 1 second
         |  max-backoff = 5 seconds
         |  number-of-retries-until-initial-wait-doubles = 5
         |  update-time = "${updateWindowStartTime.format(DateTimeFormatter.ISO_LOCAL_TIME)}"
         |  update-time-delay = ${updateDelay.toSeconds} seconds
         |}
      """.stripMargin
    )

    val actor = system.actorOf(UCThresholdManager.props(
      connectorProxy.ref,
      new TestPagerDutyAlerting(pagerDutyAlertListener.ref),
      time.scheduler,
      testTimeCalculator,
      config))

    def askForThresholdValue(thresholdActor: ActorRef): Future[Option[Double]] =
      (thresholdActor ? UCThresholdManager.GetThresholdValue)
        .mapTo[UCThresholdManager.GetThresholdValueResponse]
        .map(_.result)

    /**
     * In tests we are typically getting the actor into the ready state by having the connectorProxy reply
     * back to a request. If we had something like this:
     * ```
     *   connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
     *   connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(1.0)))
     *
     *   // should be in ready state now
     *   actor ! UCThresholdManager.GetThresholdValue
     *   expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(1.0)))
     * ```
     * Then the danger is that the final expectMsg fails. This is because when the connectorProxy
     * replies to the threshold request, it is replying to a temporary actor, not the UCThresholdManager
     * actor itself. Thus, it is possible that the connectorProxy's response doesn't reach the UCThresholdActor
     * before the `UCThresholdManager.GetThresholdValue` message above. Thus, the actor receives the latter
     * message in the wrong state. To ensure that the `UCThresholdManager.GetThresholdValue` reaches the actor
     * in the ready state, the above should be re-written as:
     * ```
     *  connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
     *  connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(1.0)))
     *
     *  // should be in ready state now
     *  awaitInReadyState()
     *  actor ! UCThresholdManager.GetThresholdValue
     *  expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(1.0)))
     * ```
     */
    def awaitInReadyState(): Unit =
      eventually{
        val probe = TestProbe()
        probe.send(actor, UCThresholdManager.GetThresholdValue)
        connectorProxy.expectNoMessage()
      }
  }

  "The ThresholdActor" when {

    "started" must {

      "ask DES for the threshold value and store the value in memory when successful" in new TestApparatus {
        timeCalculatorListener.expectMsg(TimeUntilRequest)
        timeCalculatorListener.reply(TimeUntilResponse(30.days))

        // expect the scheduled update to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(30.days))

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

        // wait until actor replies replies to Identify message to make sure it has changed state
        // before asking for threshold value
        awaitInReadyState()

        await(askForThresholdValue(actor)) shouldBe Some(12.0)
      }

      "make multiple attempts to retrieve the threshold from DES until successful (this is done under an exponential " +
        "backoff strategy) and fire pager duty alerts on each failure" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(30.days))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(30.days))

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

          // expect the retry to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(1.second))

          // make sure retry doesn't happen before it's supposed to
          time.advance(1.second - 1.milli)
          connectorProxy.expectNoMessage(1.second)

          time.advance(1.milli)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

          // make sure retry is done again
          val nextRetryTime = schedulerListener.expectMsgType[JobScheduledOnce].delay
          time.advance(nextRetryTime)

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // make sure there are no retries after success
          schedulerListener.expectNoMessage()

          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(12.0)))
        }

    }

    "handling requests to get the threshold value" must {

      "ask DES for the initial threshold value and return it when successful" in new TestApparatus {
        timeCalculatorListener.expectMsg(TimeUntilRequest)
        timeCalculatorListener.reply(TimeUntilResponse(30.days))

        // expect the scheduled update to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(30.days))

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
        pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

        // expect retry to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(1.second))

        actor ! UCThresholdManager.GetThresholdValue

        connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
        //return a Right successful case with value from DES
        connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

        expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

        time.advance(10.seconds)

        // make sure there are no retries after success
        connectorProxy.expectNoMessage()
      }

      "ask DES for the initial threshold value on startup and call DES again for the threshold value " +
        "if the intial request fails and return an error when the second request fails" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(30.days))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(30.days))

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

          // expect retry to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(1.second))

          actor ! UCThresholdManager.GetThresholdValue

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          //return a Left "error" case from DES
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

          //check None is returned
          expectMsg(UCThresholdManager.GetThresholdValueResponse(None))
        }

      "update the state with the new value when the threshold value received from DES differs from that held locally" in new TestApparatus {
        timeCalculatorListener.expectMsg(TimeUntilRequest)
        timeCalculatorListener.reply(TimeUntilResponse(30.days))

        // expect the scheduled update to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(30.days))

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
        timeCalculatorListener.expectMsg(TimeUntilRequest)
        timeCalculatorListener.reply(TimeUntilResponse(30.days))

        // expect the scheduled update to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(30.days))

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

      "return the threshold value when the first call to DES on startup is successful but an error is returned from a second call " +
        "triggered by an eligibility check" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(30.days))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(30.days))

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

          // there should be no retries
          schedulerListener.expectNoMessage()

        }

      "not schedule retries when a request has successfully returned the threshold from DES" in new TestApparatus {
        timeCalculatorListener.expectMsg(TimeUntilRequest)
        timeCalculatorListener.reply(TimeUntilResponse(30.days))

        // expect the scheduled update to be scheduled
        schedulerListener.expectMsg(JobScheduledOnce(30.days))

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

        // no retry should be scheduled
        schedulerListener.expectNoMessage()
      }

      "store the threshold value from a successful request to DES following a failed call to DES " +
        "which has been triggered on startup and cancel the scheduled retries" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(30.days))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(30.days))

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          val sender1: ActorRef = connectorProxy.sender()

          actor ! UCThresholdManager.GetThresholdValue

          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          val sender2: ActorRef = connectorProxy.sender()

          //first call to DES returns an error
          connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("No threshold found in DES")))

          // expect retry to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(1.second))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

          //second call to DES returns the value
          connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(100.0)))

          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

          // make sure we're not getting the threshold value from DES now
          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(100.0)))

          // make sure retries have been cancelled
          time.advance(10.seconds)
          connectorProxy.expectNoMessage()
        }
    }

    "handling scheduled updates" must {

        def advanceToUpdateWindow(testApparatus: TestApparatus): Unit = {
          // now make it so that the next scheduled update is in 1 hour
          testApparatus.timeCalculatorListener.expectMsg(TimeUntilRequest)
          testApparatus.timeCalculatorListener.reply(TimeUntilResponse(1.hour))

          // expect the start of the update window to be scheduled
          testApparatus.schedulerListener.expectMsg(JobScheduledOnce(1.hour))

          // get the actor in the ready state
          testApparatus.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          testApparatus.connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(10.0)))
          testApparatus.awaitInReadyState()

          // now trigger the update window
          testApparatus.time.advance(1.hour)

          // expect the end of the update window to be scheduled
          testApparatus.schedulerListener.expectMsg(JobScheduledOnce(updateDelay))

          testApparatus.actor ! UCThresholdManager.GetThresholdValue
          testApparatus.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          testApparatus.connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(11.0)))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(11.0)))
        }

      "call DES to get the threshold value when the manager receives a request to get the threshold when " +
        "the update window starts" in new TestApparatus {
          // now make it so that the next scheduled update is in 1 hour
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(1.hour))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(1.hour))

          // get the actor in the ready state
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(10.0)))

          awaitInReadyState()

          // move time forward just before the hour to make sure the
          // update window does not start - we should get the threshold value
          // from the in-memory store
          time.advance(1.hour - 1.second)
          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(10.0)))

          // now trigger the update window
          time.advance(1.second)

          // expect the end of the update window to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(updateDelay))

          // check success
          actor ! UCThresholdManager.GetThresholdValue
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(11.0)))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(11.0)))

          // check failure
          actor ! UCThresholdManager.GetThresholdValue
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("Oh no!")))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(None))
        }

      "get the threshold value from DES and store it in memory when the update window ends " +
        "and schdule the next update window" in new TestApparatus {
          advanceToUpdateWindow(this)

          // now trigger the end of the update window
          time.advance(updateDelay)
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // expect next update window to be scheduled
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(5.hours))
          schedulerListener.expectMsg(JobScheduledOnce(5.hours))

          awaitInReadyState()

          // make sure we go back to the update window state when the time elapses
          time.advance(5.hours - 1.millisecond)
          // actor should now be in ready state
          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(12.0)))

          // now we should be in update window
          time.advance(1.millisecond)
          // expect the end of the update window to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(updateDelay))

          actor ! UCThresholdManager.GetThresholdValue
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(11.0)))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(11.0)))
        }

      "retry getting the threshold value from DES if the DES retrieval fails at the end of the update window " +
        "and stop retrying if there is eventually a successful response from DES" in new TestApparatus {
          advanceToUpdateWindow(this)

          // now trigger the end of the update window
          time.advance(updateDelay)
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("oh no!")))

          // expect retry to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(1.second))
          pagerDutyAlertListener.expectMsg(TestPagerDutyAlerting.PagerDutyAlert("Could not obtain UC threshold value from DES"))

          // actor should still be in update window state
          actor ! UCThresholdManager.GetThresholdValue
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("oh no still!")))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(None))

          // make sure a retry happens
          time.advance(1.second)
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // expect next update window to be scheduled
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(5.hours))
          schedulerListener.expectMsg(JobScheduledOnce(5.hours))

          // no other retries should be triggered
          schedulerListener.expectNoMessage()

          awaitInReadyState()

          // now the actor should be in the ready state now
          actor ! UCThresholdManager.GetThresholdValue
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(12.0)))
        }

      "handle cases where a threshold request is received just after a scheduled retrieval " +
        "is triggered but before a response from DES to the scheduled retrieval is obtained and " +
        "DES responds to the scheduled retrieval request first" in {
            def doTest(test: TestApparatus ⇒ Unit) = {
              val testApparatus = new TestApparatus
              test(testApparatus)
            }

          // test when both calls to DES are successful
          doTest{ test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            // make DES reply to the second request
            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(20.0)))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // we should now be in the ready state
            test.awaitInReadyState()
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(30.0)))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))

            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))
          }

          // test when the DES response to the scheduled retrieval is successful but the one from the
          // requested retrieval is not
          doTest { test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(30.0)))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // we should be in the ready state
            test.awaitInReadyState()
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("")))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))

            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))
          }

          // test when the DES response to the scheduled retrieval is unsuccessful but the one from the
          // requested retrieval is
          doTest { test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("oh no!")))
            // expect retry to be scheduled
            test.schedulerListener.expectMsg(JobScheduledOnce(1.second))

            // make DES reply to the second request
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(20.0)))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(20.0)))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // there should be no retries
            test.schedulerListener.expectNoMessage()

            // we should now be in the ready state
            test.awaitInReadyState()
            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(20.0)))
          }

        }

      "handle cases where a threshold request is received just after a scheduled retrieval " +
        "is triggered but before a response from DES to the scheduled retrieval is obtained and " +
        "DES responds to the scheduled retrieval request second" in {
            def doTest(test: TestApparatus ⇒ Unit) = {
              val testApparatus = new TestApparatus
              test(testApparatus)
            }

          // test when both calls to DES are successful
          doTest{ test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            // make DES reply to the second request
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(20.0)))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(20.0)))
            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(30.0)))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // we should now be in the ready state
            test.awaitInReadyState()
            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))
          }

          // test when the DES response to the scheduled retrieval is successful but the one from the
          // requested retrieval is not
          doTest { test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            // make DES reply to the second request
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("")))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(None))
            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(30.0)))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // we should now be in the ready state
            test.awaitInReadyState()
            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(30.0)))
          }

          // test when the DES response to the scheduled retrieval is unsuccessful but the one from the
          // requested retrieval is
          doTest { test ⇒
            advanceToUpdateWindow(test)

            // now trigger the end of the update window
            test.time.advance(updateDelay)
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender1 = test.connectorProxy.sender()

            // request the threshold value before DES replies to the previous response
            test.actor ! UCThresholdManager.GetThresholdValue
            test.connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
            val sender2 = test.connectorProxy.sender()

            // make DES reply to the second request
            test.connectorProxy.send(sender2, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(20.0)))
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(20.0)))
            test.connectorProxy.send(sender1, UCThresholdConnectorProxyActor.GetThresholdValueResponse(Left("oh no!")))

            // expect next update window to be scheduled
            test.timeCalculatorListener.expectMsg(TimeUntilRequest)
            test.timeCalculatorListener.reply(TimeUntilResponse(5.hours))
            test.schedulerListener.expectMsg(JobScheduledOnce(5.hours))

            // there should be no retries
            test.schedulerListener.expectNoMessage()

            // we should now be in the ready state
            test.awaitInReadyState()
            test.actor ! UCThresholdManager.GetThresholdValue
            expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(20.0)))
          }
        }

      "move to the ready state if the actor starts up during the update window and " +
        "only receives a threshold value from DES after the update window" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(5.seconds))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(5.seconds))

          // expect the initial request to DES to get the threshold value
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)

          // advance time 5 seconds now
          time.advance(5.seconds)

          // then reply
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // there should be a check now done to see if we're still in the update window
          timeCalculatorListener.expectMsg(IsTimeInBetweenRequest(updateWindowStartTime, updateWindowEndTime))
          timeCalculatorListener.reply(IsTimeInBetweenResponse(false))

          // expect the next update window to be scheduled
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(3.hours))

          // we should now be in the ready state
          awaitInReadyState()
        }

      "move to the in update window state if the actor starts up during the update window and " +
        "receives a threshold value from DES within the update window" in new TestApparatus {
          timeCalculatorListener.expectMsg(TimeUntilRequest)
          timeCalculatorListener.reply(TimeUntilResponse(5.seconds))

          // expect the scheduled update to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(5.seconds))

          // expect the initial request to DES to get the threshold value
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)

          // advance time 5 seconds now
          time.advance(5.seconds)

          // then reply
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(12.0)))

          // there should be a check now done to see if we're still in the update window
          timeCalculatorListener.expectMsg(IsTimeInBetweenRequest(updateWindowStartTime, updateWindowEndTime))
          timeCalculatorListener.reply(IsTimeInBetweenResponse(true))

          // we should now be in the in-update window state
          // expect the end of the update window to be scheduled
          schedulerListener.expectMsg(JobScheduledOnce(updateDelay))
          actor ! UCThresholdManager.GetThresholdValue
          connectorProxy.expectMsg(UCThresholdConnectorProxyActor.GetThresholdValue)
          connectorProxy.reply(UCThresholdConnectorProxyActor.GetThresholdValueResponse(Right(1.0)))
          expectMsg(UCThresholdManager.GetThresholdValueResponse(Some(1.0)))
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

  class TestTimeCalculator(reportTo: ActorRef)(implicit ec: ExecutionContext) extends TimeCalculator {

    implicit val timeout: Timeout = Timeout(10.seconds)

    override def timeUntil(t: LocalTime): FiniteDuration =
      Await.result((reportTo ? TimeUntilRequest).mapTo[TimeUntilResponse].map(_.timeUntil), 10.seconds)

    override def isNowInBetween(t1: LocalTime, t2: LocalTime): Boolean =
      Await.result((reportTo ? IsTimeInBetweenRequest(t1, t2)).mapTo[IsTimeInBetweenResponse].map(_.result), 10.seconds)

  }

  object TestTimeCalculator {

    case object TimeUntilRequest

    case class TimeUntilResponse(timeUntil: FiniteDuration)

    case class IsTimeInBetweenRequest(t1: LocalTime, t2: LocalTime)

    case class IsTimeInBetweenResponse(result: Boolean)

  }

  class TestScheduler(reportTo: ActorRef, time: VirtualTime) extends MockScheduler(time) {

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = {
      val job = super.scheduleOnce(delay, runnable)
      reportTo ! JobScheduledOnce(delay)
      job
    }
  }

  object TestScheduler {
    case class JobScheduledOnce(delay: FiniteDuration)
  }

}
