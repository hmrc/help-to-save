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

import cats.instances.double._
import cats.syntax.eq._
import org.apache.pekko.actor.{Actor, ActorRef, Cancellable, Props, Scheduler}
import org.apache.pekko.pattern.{ask, pipe}
import org.apache.pekko.util.Timeout
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.UCThresholdConnectorProxyActor.{GetThresholdValue => GetDESThresholdValue, GetThresholdValueResponse => GetDESThresholdValueResponse}
import uk.gov.hmrc.helptosave.actors.UCThresholdManager._
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting, Time, WithExponentialBackoffRetry}

import java.time.LocalTime
import scala.concurrent.duration._

class UCThresholdManager(
  thresholdConnectorProxyActor: ActorRef,
  pagerDutyAlerting: PagerDutyAlerting,
  scheduler: Scheduler,
  timeCalculator: TimeCalculator,
  config: Configuration)
    extends Actor with WithExponentialBackoffRetry with Logging {
  import context.dispatcher

  private val thresholdConfig = config.get[Configuration]("uc-threshold")

  implicit val timeout: Timeout = Timeout(thresholdConfig.get[FiniteDuration]("ask-timeout"))

  val minBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("min-backoff")
  val maxBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("max-backoff")
  private val numberOfRetriesUntilWaitDoubles =
    thresholdConfig.get[Int]("number-of-retries-until-initial-wait-doubles")
  val updateWindowStartTime: LocalTime = LocalTime.parse(thresholdConfig.get[String]("update-time"))
  private val updateTimeDelay = thresholdConfig.get[FiniteDuration]("update-time-delay")
  val updateWindowEndTime: LocalTime = updateWindowStartTime.plusSeconds(updateTimeDelay.toSeconds)

  private val getDESRetries =
    exponentialBackoffRetry(
      minBackoff,
      maxBackoff,
      numberOfRetriesUntilWaitDoubles,
      self, { (_: Unit) =>
        GetDESThresholdValue
      },
      scheduler
    )

  private var updateThresholdValueJob: Option[Cancellable] = None

  private def getValueFromDES(requester: Option[ActorRef]) =
    (thresholdConnectorProxyActor ? GetDESThresholdValue)
      .mapTo[GetDESThresholdValueResponse]
      .map(r => UCThresholdManager.GetDESThresholdValueResponse(requester, r.result))

  private def scheduleStartOfUpdateWindow: Cancellable = {
    val timeUntilNextUpdateWindow = timeCalculator.timeUntil(updateWindowStartTime)
    logger.info(s"Scheduling start of update window in ${Time.nanosToPrettyString(timeUntilNextUpdateWindow.toNanos)}")
    scheduler.scheduleOnce(timeUntilNextUpdateWindow, self, UpdateWindow)
  }

  private def scheduleEndOfUpdateWindow: Cancellable = {
    logger.info(s"Scheduling end of update window in ${Time.nanosToPrettyString(updateTimeDelay.toNanos)}")
    scheduler.scheduleOnce(updateTimeDelay, self, UpdateWindow)
  }

  override def receive: Receive = notReady(updateWindowMessageReceived = false)

  private def notReady(updateWindowMessageReceived: Boolean): Receive = {
    case GetDESThresholdValue =>
      logger.info("[notReady] Trying to get UC threshold value from DES")
      getValueFromDES(None) pipeTo self

    case GetThresholdValue =>
      getValueFromDES(Some(sender())) pipeTo self

    case r: UCThresholdManager.GetDESThresholdValueResponse =>
      handleDESThresholdValueFromNotReady(r, updateWindowMessageReceived)

    case UpdateWindow =>
      logger.warn(
        "[notReady] Time to start updating threshold value but in notReady state - will reschedule update window when " +
          "DES threshold value is obtained")
      context become notReady(updateWindowMessageReceived = true)
  }

  private def ready(thresholdValue: Double): Receive = {
    case GetDESThresholdValue =>
      logger.warn("[ready] Received unexpected message: GetDESThresholdValue")

    case GetThresholdValue =>
      sender() ! GetThresholdValueResponse(Some(thresholdValue))

    case r: UCThresholdManager.GetDESThresholdValueResponse =>
      handleDESThresholdValueFromReady(r, thresholdValue)

    case UpdateWindow =>
      logger.info("[ready] Time to start updating threshold value - proceeding to updating state")
      updateThresholdValueJob = Some(scheduleEndOfUpdateWindow)
      context become inUpdateWindow(endOfWindowTriggered = false)
  }

  private def inUpdateWindow(endOfWindowTriggered: Boolean): Receive = {
    case GetDESThresholdValue =>
      logger.info("[inUpdateWindow] Trying to get UC threshold value from DES")
      getValueFromDES(None) pipeTo self

    case GetThresholdValue =>
      getValueFromDES(Some(sender())) pipeTo self

    case r: UCThresholdManager.GetDESThresholdValueResponse =>
      handleDESThresholdValueInUpdateWindow(r, endOfWindowTriggered)

    case UpdateWindow =>
      logger.info("[inUpdateWindow] End of update window reached - proceeding to retrieve value from DES")
      updateThresholdValueJob = None
      getValueFromDES(None) pipeTo self
      context become inUpdateWindow(endOfWindowTriggered = true)
  }

  private def handleDESThresholdValueFromNotReady(
    result: UCThresholdManager.GetDESThresholdValueResponse, // scalastyle:ignore method.length
    updateWindowMessageReceived: Boolean): Unit = {
    def changeStateFromNotReady(thresholdValue: Double): Unit =
      if (updateWindowMessageReceived) {
        if (timeCalculator.isNowInBetween(updateWindowStartTime, updateWindowEndTime)) {
          updateThresholdValueJob = Some(scheduleEndOfUpdateWindow)
          context become inUpdateWindow(endOfWindowTriggered = false)
        } else {
          updateThresholdValueJob = Some(scheduleStartOfUpdateWindow)
          context become ready(thresholdValue)
        }
      } else {
        context become ready(thresholdValue)
      }

    result.requester match {
      case None =>
        result.response.fold(
          { e =>
            pagerDutyAlerting.alert("Could not obtain UC threshold value from DES")
            //If Des is down, retry
            getDESRetries
              .retry(())
              .fold(
                logger.warn(
                  s"[notReady] Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                    "already scheduled - no new job scheduled")
              ) { t =>
                logger.warn(
                  s"[notReady] Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                    s"scheduled to run in ${Time.nanosToPrettyString(t.toNanos)}")
              }
          }, { value =>
            logger.info(s"[notReady] Successfully obtained the UC threshold value $value from DES")
            changeStateFromNotReady(value)
          }
        )

      case Some(requester) =>
        result.response.fold(
          { e =>
            logger.warn(s"[notReady] Could not obtain UC threshold value from DES: $e ")
            requester ! GetThresholdValueResponse(None)
          }, { value =>
            logger.info(s"[notReady] Successfully obtained the UC threshold value $value from DES")
            requester ! GetThresholdValueResponse(Some(value))
            getDESRetries.cancelAndReset()
            changeStateFromNotReady(value)
          }
        )
    }
  }

  private def handleDESThresholdValueFromReady(
    result: UCThresholdManager.GetDESThresholdValueResponse,
    currentThresholdValue: Double): Unit = {
    val value =
      result.response.fold(
        { e =>
          logger.warn(
            s"[ready] Call to get UC threshold value from DES failed: $e. But we already have a threshold value from DES")
          currentThresholdValue
        }, { value =>
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

  private def handleDESThresholdValueInUpdateWindow(
    result: UCThresholdManager.GetDESThresholdValueResponse,
    endOfWindowTriggered: Boolean): Unit = {
    def endUpdateWindow(thresholdValue: Double): Unit = {
      getDESRetries.cancelAndReset()
      updateThresholdValueJob = Some(scheduleStartOfUpdateWindow)
      context become ready(thresholdValue)
    }

    result.requester match {
      case None =>
        result.response.fold(
          { e =>
            pagerDutyAlerting.alert("Could not obtain UC threshold value from DES")

            getDESRetries
              .retry(())
              .fold {
                // we should never be in this situation
                logger.warn(
                  s"[inUpdateWindow] Could not get DES threshold value for end of update window: $e. Job to get threshold value from DES " +
                    "already exists. Not scheduling new job")
              } { t =>
                logger.warn(
                  s"[inUpdateWindow] Could not get DES threshold value for end of update window: $e. Retrying in ${Time
                    .nanosToPrettyString(t.toNanos)}")
              }
          }, { value =>
            logger.info(
              s"[inUpdateWindow] Received threshold value $value from DES at the end of update window. Proceeding to ready state")
            endUpdateWindow(value)
          }
        )

      case Some(requester) =>
        result.response.fold(
          { e =>
            logger.warn(s"[inUpdateWindow] Could not retrieve threshold value from DES: $e")
            requester ! GetThresholdValueResponse(None)
          }, { value =>
            requester ! GetThresholdValueResponse(Some(value))

            if (endOfWindowTriggered) {
              endUpdateWindow(value)

              logger.info(
                s"[inUpdateWindow] Successfully obtained the UC threshold value $value from DES. End of update window " +
                  "has been triggered. Changing to ready state")
            } else {
              logger.info(s"[inUpdateWindow] Successfully obtained the UC threshold value $value from DES")
            }
          }
        )
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    updateThresholdValueJob = Some(scheduleStartOfUpdateWindow)
    self ! GetDESThresholdValue
  }

  override def postStop(): Unit = {
    super.postStop()
    updateThresholdValueJob.foreach(_.cancel())
    getDESRetries.cancelAndReset()
  }
}

object UCThresholdManager {
  private case class GetDESThresholdValueResponse(requester: Option[ActorRef], response: Either[String, Double])

  /** Message used to trigger the start of an update window and to end the update window */
  private case object UpdateWindow

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Option[Double])

  def props(
    thresholdConnectorProxy: ActorRef,
    pagerDutyAlerting: PagerDutyAlerting,
    scheduler: Scheduler,
    timeCalculator: TimeCalculator,
    config: Configuration): Props =
    Props(new UCThresholdManager(thresholdConnectorProxy, pagerDutyAlerting, scheduler, timeCalculator, config))
}
