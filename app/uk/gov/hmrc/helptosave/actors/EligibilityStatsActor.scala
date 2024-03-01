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

import org.apache.pekko.actor.{Actor, Cancellable, Props, Scheduler}
import org.apache.pekko.pattern.pipe
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.EligibilityStatsActor.{GetStats, GetStatsResponse}
import uk.gov.hmrc.helptosave.actors.EligibilityStatsParser.{EligibilityReason, Source, Table}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.util.{Logging, Time}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

class EligibilityStatsActor(
  scheduler: Scheduler,
  config: Configuration,
  eligibilityStatsStore: EligibilityStatsStore,
  eligibilityStatsParser: EligibilityStatsParser,
  metrics: Metrics)
    extends Actor with Logging {
  import context.dispatcher

  private var eligibilityStatsJob: Option[Cancellable] = None

  private var registeredStats = Set.empty[(EligibilityReason, Source)]

  // use a thread safe map as the gauges we register with require access to this
  // table as well as this actor
  private val statsTable =
    TrieMap.empty[EligibilityReason, TrieMap[Source, Int]]

  override def receive: Receive = {
    case GetStats =>
      logger.info("Getting eligibility stats from mongo")
      eligibilityStatsStore.getEligibilityStats.map(GetStatsResponse) pipeTo self

    case r: GetStatsResponse =>
      val table = eligibilityStatsParser.createTable(r.result)
      updateLocalStats(table)
      updateMetrics(table)
      outputReport(table)
  }

  private def updateMetrics(table: Table): Unit = {
    def replaceSpaces(s: String) = s.replaceAll(" ", "-")

    for ((reason, channels) <- table) {
      for ((channel, _) <- channels) {
        if (!registeredStats.contains(reason -> channel)) {
          logger.info(s"Registering gauge for (reason, channel) = ($reason, $channel) ")
          metrics.registerAccountStatsGauge(
            replaceSpaces(reason),
            replaceSpaces(channel),
            () => statsTable.get(reason).flatMap(_.get(channel)).getOrElse(0)
          )
          registeredStats += reason -> channel
        }
      }
    }
  }

  private def scheduleStats(): Cancellable = {
    val initialDelay = config.get[FiniteDuration]("eligibility-stats.initial-delay")
    val frequency = config.get[FiniteDuration]("eligibility-stats.frequency")
    logger.info(s"Scheduling eligibility-stats job in ${Time.nanosToPrettyString(initialDelay.toNanos)}")
    scheduler.scheduleAtFixedRate(initialDelay, frequency, self, GetStats)
  }

  private def updateLocalStats(table: Table): Unit = {
    statsTable.clear()
    table.foreach {
      case (reason, stats) =>
        statsTable.update(reason, TrieMap(stats.toList: _*))
    }
  }

  private def outputReport(table: Table): Unit = {
    val formattedTable = eligibilityStatsParser.prettyFormatTable(table)
    logger.info(s"report is $formattedTable")
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

  def props(
    scheduler: Scheduler,
    config: Configuration,
    eligibilityStatsStore: EligibilityStatsStore,
    eligibilityStatsParser: EligibilityStatsParser,
    metrics: Metrics): Props =
    Props(new EligibilityStatsActor(scheduler, config, eligibilityStatsStore, eligibilityStatsParser, metrics))
}
