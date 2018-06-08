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

import java.time.{Clock, LocalTime}
import java.util.TimeZone

import cats.instances.double._
import cats.syntax.eq._
import configs.syntax._
import akka.actor.{Actor, ActorRef, Cancellable, Props, Scheduler}
import uk.gov.hmrc.helptosave.actors.UCThresholdManager._
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting, Time, WithExponentialBackoffRetry}

import scala.concurrent.duration._
import scala.concurrent.Future
import UCThresholdConnectorProxyActor.{GetThresholdValue ⇒ GetDESThresholdValue, GetThresholdValueResponse ⇒ GetDESThresholdValueResponse}

class UCThresholdManager(thresholdConnectorProxyActor: ActorRef,
                         pagerDutyAlerting:            PagerDutyAlerting,
                         scheduler:                    Scheduler,
                         timeCalculator:               TimeCalculator,
                         config:                       Config) extends Actor with WithExponentialBackoffRetry with Logging {

  import context.dispatcher

  val thresholdConfig = config.get[Config]("uc-threshold").value

  implicit val timeout: Timeout = Timeout(thresholdConfig.get[FiniteDuration]("ask-timeout").value)

  val minBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("min-backoff").value
  val maxBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("max-backoff").value
  val numberOfRetriesUntilWaitDoubles: Int = thresholdConfig.get[Int]("number-of-retries-until-initial-wait-doubles").value
  val updateWindowStartTime: LocalTime = LocalTime.parse(thresholdConfig.get[String]("update-time").value)
  val updateTimeDelay: FiniteDuration = thresholdConfig.get[FiniteDuration]("update-time-delay").value
  val updateWindowEndTime: LocalTime = updateWindowStartTime.plusSeconds(updateTimeDelay.toSeconds)

  val getDESRetries =
    exponentialBackoffRetry(
      minBackoff,
      maxBackoff,
      numberOfRetriesUntilWaitDoubles,
      self,
      { _: Unit ⇒ GetDESThresholdValue },
      scheduler
    )

  var updateThresholdValueJob: Option[Cancellable] = None

  def getValueFromDES(requester: Option[ActorRef]): Future[UCThresholdManager.GetDESThresholdValueResponse] =
    (thresholdConnectorProxyActor ? GetDESThresholdValue)
      .mapTo[GetDESThresholdValueResponse]
      .map(r ⇒ UCThresholdManager.GetDESThresholdValueResponse(requester, r.result))

  def scheduleStartOfUpdateWindow(): Cancellable = {
    val timeUntilNextUpdateWindow = timeCalculator.timeUntil(updateWindowStartTime)
    logger.info(s"Scheduling start of update window in ${Time.nanosToPrettyString(timeUntilNextUpdateWindow.toNanos)}")
    scheduler.scheduleOnce(timeUntilNextUpdateWindow, self, UpdateWindow)
  }

  def scheduleEndOfUpdateWindow(): Cancellable = {
    val timeUntilEndOfUpdateWindow = timeCalculator.timeUntil(updateWindowStartTime)
    logger.info(s"Scheduling end of update window in ${Time.nanosToPrettyString(timeUntilEndOfUpdateWindow.toNanos)}")
    scheduler.scheduleOnce(updateTimeDelay, self, UpdateWindow)
  }

  override def receive: Receive = notReady(updateWindowMessageReceived = false)

  def notReady(updateWindowMessageReceived: Boolean): Receive = {

    case GetDESThresholdValue ⇒
      logger.info("[notReady] Trying to get UC threshold value from DES")
      getValueFromDES(None) pipeTo self

    case GetThresholdValue ⇒
      getValueFromDES(Some(sender())) pipeTo self

    case r: UCThresholdManager.GetDESThresholdValueResponse ⇒
      handleDESThresholdValueFromNotReady(r, updateWindowMessageReceived)

    case UpdateWindow ⇒
      logger.warn("[notReady] Time to start updating threshold value but in notReady state - will reschedule update window when " +
        "DES threshold value is obtained")
      context become notReady(updateWindowMessageReceived = true)
  }

  def ready(thresholdValue: Double): Receive = {

    case GetDESThresholdValue ⇒
      logger.warn("[ready] Received unexpected message: GetDESThresholdValue")

    case GetThresholdValue ⇒
      sender() ! GetThresholdValueResponse(Some(thresholdValue))

    case r: UCThresholdManager.GetDESThresholdValueResponse ⇒
      handleDESThresholdValueFromReady(r, thresholdValue)

    case UpdateWindow ⇒
      logger.info("[ready] Time to start updating threshold value - proceeding to updating state")
      updateThresholdValueJob = Some(scheduleEndOfUpdateWindow())
      context become inUpdateWindow(endOfWindowTriggered = false)
  }

  def inUpdateWindow(endOfWindowTriggered: Boolean): Receive = {
    case GetDESThresholdValue ⇒
      logger.info("[inUpdateWindow] Trying to get UC threshold value from DES")
      getValueFromDES(None) pipeTo self

    case GetThresholdValue ⇒
      getValueFromDES(Some(sender())) pipeTo self

    case r: UCThresholdManager.GetDESThresholdValueResponse ⇒
      handleDESThresholdValueInUpdateWindow(r, endOfWindowTriggered)

    case UpdateWindow ⇒
      logger.info("[inUpdateWindow] End of update window reached - proceeding to retrieve value from DES")
      updateThresholdValueJob = None
      getValueFromDES(None) pipeTo self
      context become inUpdateWindow(endOfWindowTriggered = true)
  }

  def handleDESThresholdValueFromNotReady(result:                      UCThresholdManager.GetDESThresholdValueResponse, // scalastyle:ignore method.length
                                          updateWindowMessageReceived: Boolean): Unit = {
      def changeStateFromNotReady(thresholdValue: Double): Unit =
        if (updateWindowMessageReceived) {
          if (timeCalculator.isNowInBetween(updateWindowStartTime, updateWindowEndTime)) {
            updateThresholdValueJob = Some(scheduleEndOfUpdateWindow())
            context become inUpdateWindow(endOfWindowTriggered = false)
          } else {
            updateThresholdValueJob = Some(scheduleStartOfUpdateWindow())
            context become ready(thresholdValue)
          }
        } else {
          context become ready(thresholdValue)
        }

    result.requester match {
      case None ⇒
        result.response.fold({
          e ⇒
            pagerDutyAlerting.alert("Could not obtain UC threshold value from DES")
            //If Des is down, retry
            getDESRetries.retry(()).fold(
              logger.warn(s"[notReady] Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                "already scheduled - no new job scheduled")
            ) {
                t ⇒
                  logger.warn(s"[notReady] Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                    s"scheduled to run in ${
                      Time.nanosToPrettyString(t.toNanos)
                    }")
              }
        }, {
          value ⇒
            logger.info(s"[notReady] Successfully obtained the UC threshold value $value from DES")
            changeStateFromNotReady(value)
        })

      case Some(requester) ⇒
        result.response.fold({
          e ⇒
            logger.warn(s"[notReady] Could not obtain UC threshold value from DES: $e ")
            requester ! GetThresholdValueResponse(None)
        }, {
          value ⇒
            logger.info(s"[notReady] Successfully obtained the UC threshold value $value from DES")
            requester ! GetThresholdValueResponse(Some(value))
            getDESRetries.cancelAndReset()
            changeStateFromNotReady(value)
        })
    }

  }

  def handleDESThresholdValueFromReady(result: UCThresholdManager.GetDESThresholdValueResponse, currentThresholdValue: Double): Unit = {
    val value =
      result.response.fold(
        {
          e ⇒
            logger.warn(s"[ready] Call to get UC threshold value from DES failed: $e. But we already have a threshold value from DES")
            currentThresholdValue
        }, {
          value ⇒
            logger.info(s"[ready] Successfully obtained the UC threshold value $value from DES")
            value
        }
      )

    if (currentThresholdValue =!= value) {
      context become ready(value)
      logger.info(s"[ready] UC Threshold has changed, the value is now: $value")
    }

    getDESRetries.cancelAndReset()
    result.requester.foreach {
      _ ! GetThresholdValueResponse(Some(value))
    }
  }

  def handleDESThresholdValueInUpdateWindow(result:               UCThresholdManager.GetDESThresholdValueResponse,
                                            endOfWindowTriggered: Boolean): Unit = {
      def endUpdateWindow(thresholdValue: Double): Unit = {
        getDESRetries.cancelAndReset()
        updateThresholdValueJob = Some(scheduleStartOfUpdateWindow())
        context become ready(thresholdValue)
      }

    result.requester match {
      case None ⇒
        result.response.fold({
          e ⇒
            pagerDutyAlerting.alert("Could not obtain UC threshold value from DES")

            getDESRetries.retry(()).fold {
              // we should never be in this situation
              logger.warn(s"[inUpdateWindow] Could not get DES threshold value for end of update window: $e. Job to get threshold value from DES " +
                "already exists. Not scheduling new job")
            } {
              t ⇒
                logger.warn(s"[inUpdateWindow] Could not get DES threshold value for end of update window: $e. Retrying in ${
                  Time.nanosToPrettyString(t.toNanos)
                }")
            }
        }, {
          value ⇒
            logger.info(s"[inUpdateWindow] Received threshold value $value from DES at the end of update window. Proceeding to ready state")
            endUpdateWindow(value)
        })

      case Some(requester) ⇒
        result.response.fold({ e ⇒
          logger.warn(s"[inUpdateWindow] Could not retrieve threshold value from DES: $e")
          requester ! GetThresholdValueResponse(None)
        }, { value ⇒
          requester ! GetThresholdValueResponse(Some(value))

          if (endOfWindowTriggered) {
            endUpdateWindow(value)

            logger.info(s"[inUpdateWindow] Successfully obtained the UC threshold value $value from DES. End of update window " +
              s"has been triggered. Changing to ready state")
          } else {
            logger.info(s"[inUpdateWindow] Successfully obtained the UC threshold value $value from DES")
          }
        })
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    updateThresholdValueJob = Some(scheduleStartOfUpdateWindow())
    self ! GetDESThresholdValue
  }

  override def postStop(): Unit = {
    super.postStop()
    updateThresholdValueJob.foreach(_.cancel())
    getDESRetries.cancelAndReset()
  }

}

object UCThresholdManager {

  private[actors] case class GetDESThresholdValueResponse(requester: Option[ActorRef], response: Either[String, Double])

  /** Message used to trigger the start of an update window and to end the update window */
  private case object UpdateWindow

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Option[Double])

  def props(thresholdConnectorProxy: ActorRef,
            pagerDutyAlerting:       PagerDutyAlerting,
            scheduler:               Scheduler,
            timeCalculator:          TimeCalculator,
            config:                  Config): Props =
    Props(new UCThresholdManager(thresholdConnectorProxy, pagerDutyAlerting, scheduler, timeCalculator, config))
}
