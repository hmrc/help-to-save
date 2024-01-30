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

import java.time.Clock

import akka.actor.ActorRef
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import com.codahale.metrics.{Counter, Gauge, Timer}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.helptosave.actors.EligibilityStatsActor.{GetStats, GetStatsResponse}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import com.kenshoo.play.metrics.{Metrics => PlayMetrics}
import uk.gov.hmrc.helptosave.actors.EligibilityStatsParser.Table

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

class EligibilityStatsActorSpec extends ActorTestSupport("EligibilityStatsActorSpec") with Eventually {
  import TestApparatus._

  implicit val timeout: Timeout = Timeout(10.seconds)

  class TestApparatus {

    val connectorProxy = TestProbe()
    val timeCalculatorListener = TestProbe()
    val schedulerListener = TestProbe()
    val metricsListener = TestProbe()
    val eligibilityStatsStoreListener = TestProbe()
    val eligibilityStatsParserListener = TestProbe()

    val timeCalculator = new TimeCalculatorImpl(Clock.systemUTC())

    val config = ConfigFactory.parseString(
      """
         |eligibility-stats {
         |    enabled = true
         |    initial-delay  = 5 minutes
         |    frequency = 1 hour
         |}
    """.stripMargin
    )

    val actor = system.actorOf(EligibilityStatsActor.props(
      system.scheduler,
      config,
      timeCalculator,
      new TestEligibilityStatsStore(eligibilityStatsStoreListener.ref),
      new TestEligibilityStatsParser(eligibilityStatsParserListener.ref),
      new MockMetrics(metricsListener.ref)))
  }

  object TestApparatus {

    class MockMetrics(reportTo: ActorRef) extends Metrics(stub[PlayMetrics]) {
      override def timer(name: String): Timer = new Timer()

      override def counter(name: String): Counter = new Counter()

      override def registerIntGauge(name: String, gauge: Gauge[Int]): Gauge[Int] = {
        reportTo ! MockMetrics.GaugeRegistered(name, gauge)
        gauge
      }

    }

    object MockMetrics {
      case class GaugeRegistered(name: String, gauge: Gauge[Int])
    }

    class TestEligibilityStatsStore(reportTo: ActorRef) extends EligibilityStatsStore {
      def getEligibilityStats: Future[List[EligibilityStats]] = {
        (reportTo ? GetStats).mapTo[GetStatsResponse].map(_.result)
      }
    }

    class TestEligibilityStatsParser(reportTo: ActorRef) extends EligibilityStatsParserImpl {
      override def createTable(result: List[EligibilityStats]): Table = {
        reportTo ! TestEligibilityStatsParser.CreateTableRequestReceived(result)
        super.createTable(result)

      }

      override def prettyFormatTable(table: Table): String = {
        reportTo ! TestEligibilityStatsParser.PrettyFormatTableRequestReceived(table)
        super.prettyFormatTable(table)
      }

    }

    object TestEligibilityStatsParser {
      case class CreateTableRequestReceived(stats: List[EligibilityStats])
      case class PrettyFormatTableRequestReceived(table: Table)
    }

  }

  def replaceSpaces(s: String): String = s.replaceAll(" ", "-")

  "EligibilityStatsActor" when {
    "retrieving the stats from mongo store" must {

        def checkRegisteredGauges(metricsListener: TestProbe,
                                  expectedGauges:  List[(String, Int)]
        ): Unit = {
            @tailrec
            def loop(remaining: List[(String, Int)]): Unit = remaining match {
              case Nil =>
                metricsListener.expectNoMessage()

              case l =>
                val registered = metricsListener.expectMsgType[MockMetrics.GaugeRegistered]
                val entry @ (_, count) =
                  l.find(_._1 === registered.name).getOrElse(fail(s"Encountered unexpected gauge name: ${registered.name}"))
                registered.gauge.getValue shouldBe count
                loop(l.filterNot(_ === entry))
            }

          loop(expectedGauges)
        }

      "handle stats returned from mongo" in new TestApparatus {
        val stats = List(EligibilityStats(Some(1), Some("some source"), 2))
        val table = Map("1" -> Map("some source" -> 2))

        // trigger the process
        actor ? GetStats

        // give the actor the stats
        eligibilityStatsStoreListener.expectMsg(GetStats)
        eligibilityStatsStoreListener.reply(GetStatsResponse(stats))

        // now a conversion to a table should be requested
        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.CreateTableRequestReceived(stats))

        // gauge should now be registered
        val registered = metricsListener.expectMsgType[MockMetrics.GaugeRegistered]
        metricsListener.expectNoMessage()

        registered.name shouldBe "backend.create-account.1.some-source"
        registered.gauge.getValue shouldBe 2

        // now the table should be pretty printed
        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.PrettyFormatTableRequestReceived(table))

        // now check that gauges that have already been registered don't get registered again
        val newStats = EligibilityStats(Some(2), Some("new source"), 3) :: stats
        val newTable = Map(
          "1" -> Map("some source" -> 2, "new source" -> 0),
          "2" -> Map("some source" -> 0, "new source" -> 3)
        )

        // we won't expect "backend.create-account.1.some-source" because it has already been registered
        val expectedGauges =
          List(
            ("backend.create-account.1.new-source", 0),
            ("backend.create-account.2.some-source", 0),
            ("backend.create-account.2.new-source", 3)
          )

        actor ? GetStats
        eligibilityStatsStoreListener.expectMsg(GetStats)
        eligibilityStatsStoreListener.reply(GetStatsResponse(newStats))
        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.CreateTableRequestReceived(newStats))

        checkRegisteredGauges(metricsListener, expectedGauges)
        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.PrettyFormatTableRequestReceived(newTable))

      }

    }
  }
}

