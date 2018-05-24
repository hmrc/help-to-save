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

import configs.syntax._
import akka.actor.{Actor, ActorRef, Props, Scheduler}
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
                         config:                       Config) extends Actor with WithExponentialBackoffRetry with Logging {

  import context.dispatcher

  val thresholdConfig = config.get[Config]("uc-threshold").value

  implicit val timeout: Timeout = Timeout(thresholdConfig.get[FiniteDuration]("ask-timeout").value)

  val minBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("min-backoff").value
  val maxBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("max-backoff").value
  val numberOfRetriesUntilWaitDoubles: Int = thresholdConfig.get[Int]("number-of-retries-until-initial-wait-doubles").value

  val getDESRetries =
    exponentialBackoffRetry(
      minBackoff,
      maxBackoff,
      numberOfRetriesUntilWaitDoubles,
      self,
      { _: Unit ⇒ GetDESThresholdValue },
      scheduler
    )

  def getValueFromDES(): Future[GetDESThresholdValueResponse] =
    (thresholdConnectorProxyActor ? GetDESThresholdValue)
      .mapTo[GetDESThresholdValueResponse]

  override def receive: Receive = notReady

  def notReady: Receive = {

    case GetDESThresholdValue ⇒
      logger.info("Trying to get UC threshold value from DES")
      getValueFromDES() pipeTo self

    case GetDESThresholdValueResponse(result) ⇒
      result.fold(
        { e ⇒
          pagerDutyAlerting.alert("Could not obtain initial UC threshold value from DES")
          //If Des is down, retry
          getDESRetries.retry(()).fold(
            logger.warn(s"Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
              "already scheduled - no new job scheduled")
          ){ t ⇒
              logger.warn(s"Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                s"scheduled to run in ${Time.nanosToPrettyString(t.toNanos)}")
            }
        }, {
          value ⇒
            logger.info(s"Successfully obtained the UC threshold value $value from DES")
            context become ready(value)
        }
      )

  }

  def ready(thresholdValue: Double): Receive = {

    case GetThresholdValue ⇒
      sender() ! GetThresholdValueResponse(thresholdValue)

  }

  override def preStart(): Unit = {
    super.preStart()
    self ! GetDESThresholdValue
  }

  override def postStop(): Unit = {
    super.postStop()
    getDESRetries.cancelAndReset()
  }

}

object UCThresholdManager {

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Double)

  def props(thresholdConnectorProxy: ActorRef,
            pagerDutyAlerting:       PagerDutyAlerting,
            scheduler:               Scheduler,
            config:                  Config): Props =
    Props(new UCThresholdManager(thresholdConnectorProxy, pagerDutyAlerting, scheduler, config))
}
