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

import java.time.{Clock, ZoneId}

import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.helptosave.actors.EligibilityStatsActor.GetStats
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats

import scala.concurrent.Future
import scala.concurrent.duration._

class EligibilityStatsActorSpec extends ActorTestSupport("EligibilityStatsActorSpec") with Eventually {

  class TestApparatus {

    implicit val timeout: Timeout = Timeout(10.seconds)

    val eligibulityStatsStore = mock[EligibilityStatsStore]
    val eligibilityStatsHandler = stub[EligibilityStatsHandler]
    val connectorProxy = TestProbe()
    val timeCalculatorListener = TestProbe()
    val schedulerListener = TestProbe()

    private val timeCalculator = {
      val clock = Clock.system(ZoneId.of(configuration.underlying.getString("eligibility-stats.timezone")))
      new TimeCalculatorImpl(clock)
    }

    val config = ConfigFactory.parseString(
      s"""
         |eligibility-stats {
         |    enabled = true
         |    timezone   = "Europe/London"
         |    trigger-time       = "01:00"
         |}
    """.stripMargin
    )

    val actor = system.actorOf(EligibilityStatsActor.props(
      system.scheduler,
      config,
      timeCalculator,
      eligibulityStatsStore,
      eligibilityStatsHandler))
  }

  "EligibilityStatsActor" when {
    "retrieving the stats from mongo store" must {
      "handle stats returned from mongo" in new TestApparatus {
        val stats = List(EligibilityStats(Some(6), Some("Digital"), 1))

        (eligibulityStatsStore.getEligibilityStats: () ⇒ Future[List[EligibilityStats]]).expects().returning(Future.successful(stats))

        actor ? GetStats

        Thread.sleep(2000)

        (eligibilityStatsHandler.handleStats(_: List[EligibilityStats])).verify(stats)
      }
    }
  }
}
