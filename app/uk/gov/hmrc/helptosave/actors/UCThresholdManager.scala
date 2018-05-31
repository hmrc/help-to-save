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

import cats.instances.double._
import cats.syntax.eq._
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

  def getValueFromDES(requester: Option[ActorRef]): Future[UCThresholdManager.GetDESThresholdValueResponse] =
    (thresholdConnectorProxyActor ? GetDESThresholdValue)
      .mapTo[GetDESThresholdValueResponse]
      .map(r ⇒ UCThresholdManager.GetDESThresholdValueResponse(requester, r.result))

  override def receive: Receive = notReady

  def notReady: Receive = {

    case GetDESThresholdValue ⇒
      logger.info("Trying to get UC threshold value from DES")
      getValueFromDES(None) pipeTo self

    case GetThresholdValue ⇒
      getValueFromDES(Some(sender())) pipeTo self

    case UCThresholdManager.GetDESThresholdValueResponse(maybeRequester, result) ⇒
      maybeRequester match {
        case None ⇒
          result.fold ({
            e ⇒
              pagerDutyAlerting.alert ("Could not obtain initial UC threshold value from DES")
              //If Des is down, retry
              getDESRetries.retry (()).fold (
                logger.warn (s"Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                  "already scheduled - no new job scheduled")
              ) {
                  t ⇒
                    logger.warn (s"Could not obtain initial UC threshold value from DES: $e. Job to retry getting value from DES " +
                      s"scheduled to run in ${
                        Time.nanosToPrettyString (t.toNanos)
                      }")
                }
          }, {
            value ⇒
              logger.info (s"Successfully obtained the UC threshold value $value from DES")
              context become ready (value)
          })

        case Some(requester) ⇒
          result.fold ({
            e ⇒
              logger.warn(s"Could not obtain UC threshold value from DES: $e ")
              requester ! GetThresholdValueResponse(None)
          }, {
            value ⇒
              logger.info (s"Successfully obtained the UC threshold value $value from DES")
              requester ! GetThresholdValueResponse(Some(value))
              getDESRetries.cancelAndReset()
              context become ready (value)
          })
      }

  }

  def ready(thresholdValue: Double): Receive = {

    case GetThresholdValue ⇒
      sender() ! GetThresholdValueResponse(Some(thresholdValue))

    case UCThresholdManager.GetDESThresholdValueResponse(maybeRequester, result) ⇒
      val value =
        result.fold(
          {
            e ⇒
              logger.warn (s"Call to get UC threshold value from DES failed: $e. But we already have an initial value from DES")
              thresholdValue
          }, {
            value ⇒
              logger.info (s"Successfully obtained the UC threshold value $value from DES")
              value
          }
        )

      if (thresholdValue =!= value) {
        context become ready(value)
        logger.info(s"UC Threshold has changed, the value is now: $value")
      }

      getDESRetries.cancelAndReset()
      maybeRequester.foreach{ _ ! GetThresholdValueResponse(Some(value)) }
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

  case class GetDESThresholdValueResponse(requester: Option[ActorRef], response: Either[String, Double])

  case object GetThresholdValue

  case class GetThresholdValueResponse(result: Option[Double])

  def props(thresholdConnectorProxy: ActorRef,
            pagerDutyAlerting:       PagerDutyAlerting,
            scheduler:               Scheduler,
            config:                  Config): Props =
    Props(new UCThresholdManager(thresholdConnectorProxy, pagerDutyAlerting, scheduler, config))
}
