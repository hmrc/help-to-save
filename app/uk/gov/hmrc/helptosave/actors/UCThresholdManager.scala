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
import akka.actor.{Actor, ActorRef, Props, Scheduler, Stash}
import uk.gov.hmrc.helptosave.actors.UCThresholdManager._
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting, WithExponentialBackoffRetry}

import scala.concurrent.duration._
import scala.concurrent.Future

import UCThresholdConnectorProxy.{GetThresholdValue ⇒ GetDESThresholdValue, GetThresholdValueResponse ⇒ GetDESThresholdValueResponse}
import UCThresholdMongoProxy.{GetThresholdValue ⇒ GetMongoThresholdValue, GetThresholdValueResponse ⇒ GetMongoThresholdValueResponse}
import UCThresholdMongoProxy.{StoreThresholdValue ⇒ StoreMongoThresholdValue, StoreThresholdValueResponse ⇒ StoreMongoThresholdValueResponse}

class UCThresholdManager(thresholdConnectorProxy: ActorRef,
                         thresholdMongoProxy:     ActorRef,
                         pagerDutyAlerting:       PagerDutyAlerting,
                         scheduler:               Scheduler,
                         config:                  Config) extends Actor with WithExponentialBackoffRetry with Stash with Logging {

  import context.dispatcher

  val thresholdConfig = config.get[Config]("uc-threshold").value

  implicit val timeout: Timeout = Timeout(thresholdConfig.get[FiniteDuration]("ask-timeout").value)

  val minBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("min-backoff").value
  val maxBackoff: FiniteDuration = thresholdConfig.get[FiniteDuration]("max-backoff").value
  val numberOfRetriesUntilWaitDoubles: Int = thresholdConfig.get[Int]("number-of-retries-until-initial-wait-doubles").value

  val storeMongoRetries = exponentialBackoffRetry(
    minBackoff,
    maxBackoff,
    numberOfRetriesUntilWaitDoubles,
    self,
    { StoreMongoThresholdValue.apply },
    scheduler
  )

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
    (thresholdConnectorProxy ? GetDESThresholdValue)
      .mapTo[GetDESThresholdValueResponse]

  def storeValue(amount: Double): Future[StoreMongoThresholdValueResponse] =
    (thresholdMongoProxy ? StoreMongoThresholdValue(amount))
      .mapTo[StoreMongoThresholdValueResponse]

  def getValueFromMongo(): Future[GetMongoThresholdValueResponse] =
    (thresholdMongoProxy ? GetMongoThresholdValue)
      .mapTo[GetMongoThresholdValueResponse]

  override def receive: Receive = notReady

  def notReady: Receive = {

    case GetDESThresholdValue ⇒
      logger.info("Trying to get UC threshold value from DES")
      getValueFromDES() pipeTo self

    case GetDESThresholdValueResponse(result) ⇒
      handleDESThresholdValue(result)

    case GetMongoThresholdValue ⇒
      logger.info("Trying to get UC threshold value from mongo")
      getValueFromMongo() pipeTo self

    case GetMongoThresholdValueResponse(result) ⇒
      handleMongoThresholdValue(result)

    case GetThresholdValue ⇒
      stash()
  }

  def ready(thresholdValue: Double): Receive = {

    case GetThresholdValue ⇒
      sender() ! GetThresholdValueResponse(thresholdValue)

    case _: StoreMongoThresholdValue ⇒
      // write value to mongo
      logger.info("Trying to store UC threshold value in mongo")
      storeValue(thresholdValue) pipeTo self

    case StoreMongoThresholdValueResponse(result) ⇒
      result.fold({
        e ⇒
          storeMongoRetries.retry(thresholdValue).fold(
            logger.warn("Error while trying to store UC threshold value in mongo: retry job already active, new retry not scheduled")
          ){ time ⇒
              logger.warn(s"Error while trying to store UC threshold value in mongo: $e. Retrying in ${time.toString()}")
            }
      }, {
        value ⇒
          logger.info(s"UC threshold was successfully saved in mongo, value: $value")
          storeMongoRetries.cancelAndReset()
      })

  }

  def handleDESThresholdValue(result: Either[String, Double]): Unit = result.fold(
    { e ⇒
      logger.warn(s"Could not obtain the UC threshold value from DES: $e")
      //If Des is down, try mongo
      self ! GetMongoThresholdValue
    }, {
      value ⇒
        logger.info(s"Successfully obtained the UC threshold value $value from DES")
        context become ready(value)
        self ! StoreMongoThresholdValue(value)
        unstashAll()
    }
  )

  def handleMongoThresholdValue(result: Either[String, Option[Double]]): Unit = {
      def handleError(): Unit = {
        getDESRetries.retry(())
        pagerDutyAlerting.alert("Could not initialise UC threshold value from mongo")
      }

    result.fold({
      e ⇒
        logger.warn(s"Error while getting threshold value from mongo: $e")
        handleError()
    }, {
      _.fold {
        logger.warn("Could not find threshold value in mongo")
        handleError()
      } { value ⇒
        logger.info("Successfully obtained threshold value from mongo")
        context become ready(value)
        unstashAll()
      }
    })
  }

  override def preStart(): Unit = {
    super.preStart()
    self ! GetDESThresholdValue
  }

  override def postStop(): Unit = {
    super.postStop()
    storeMongoRetries.cancelAndReset()
    getDESRetries.cancelAndReset()
  }

}

object UCThresholdManager {

  sealed trait Command

  case object GetThresholdValue extends Command

  case class GetThresholdValueResponse(result: Double)

  def props(thresholdConnectorProxy: ActorRef,
            thresholdMongoProxy:     ActorRef,
            pagerDutyAlerting:       PagerDutyAlerting,
            scheduler:               Scheduler,
            config:                  Config): Props =
    Props(new UCThresholdManager(thresholdConnectorProxy, thresholdMongoProxy, pagerDutyAlerting, scheduler, config))
}
