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

import java.time.LocalTime

import akka.actor.{Actor, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import com.typesafe.config.Config
import configs.syntax._
import uk.gov.hmrc.helptosave.actors.EligibilityStatsActor.{GetStats, GetStatsResponse}
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.util.{Logging, Time}

import scala.concurrent.duration.FiniteDuration

class EligibilityStatsActor(scheduler:               Scheduler,
                            config:                  Config,
                            timeCalculator:          TimeCalculator,
                            eligibilityStatsStore:   EligibilityStatsStore,
                            eligibilityStatsHandler: EligibilityStatsHandler) extends Actor with Logging {

  import context.dispatcher

  var eligibilityStatsJob: Option[Cancellable] = None

  override def receive: Receive = {
    case GetStats ⇒
      logger.info("Getting eligibility stats from mongo")
      eligibilityStatsStore.getEligibilityStats().map(GetStatsResponse) pipeTo self

    case r: GetStatsResponse ⇒
      eligibilityStatsHandler.handleStats(r.result)
  }

  def scheduleStats(): Cancellable = {
    val scheduleStart = LocalTime.parse(config.getString("eligibility-stats.trigger-time"))
    val frequency = config.get[FiniteDuration]("eligibility-stats.frequency").value
    val timeUntilNextTrigger = timeCalculator.timeUntil(scheduleStart)
    logger.info(s"Scheduling eligibility stats job in ${Time.nanosToPrettyString(timeUntilNextTrigger.toNanos)}")
    scheduler.schedule(timeUntilNextTrigger, frequency, self, GetStats)
  }

  override def preStart(): Unit = {
    super.preStart()
    eligibilityStatsJob = Some(scheduleStats())
  }

  override def postStop(): Unit = {
    super.postStop()
    eligibilityStatsJob.foreach(_.cancel())
  }
}

object EligibilityStatsActor {

  case object GetStats

  case class GetStatsResponse(result: List[EligibilityStats])

  def props(scheduler:               Scheduler,
            config:                  Config,
            timeCalculator:          TimeCalculator,
            eligibilityStatsStore:   EligibilityStatsStore,
            eligibilityStatsHandler: EligibilityStatsHandler): Props =
    Props(new EligibilityStatsActor(scheduler, config, timeCalculator, eligibilityStatsStore, eligibilityStatsHandler))
}
