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
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import uk.gov.hmrc.helptosave.actors.EligibilityStatsParser.Table

import scala.concurrent.Future
import scala.concurrent.duration._

class EligibilityStatsActorSpec extends ActorTestSupport("EligibilityStatsActorSpec") with Eventually {
  import TestApparatus._

  import system.dispatcher

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

      override def registerGauge[A](name: String, gauge: Gauge[A]): Gauge[A] = {
        gauge match {
          case g: Gauge[Long] ⇒
            reportTo ! MockMetrics.GaugeRegistered(name, g)
            gauge

          case _ ⇒ fail()
        }

      }

    }

    object MockMetrics {
      case class GaugeRegistered(name: String, gauge: Gauge[Long])
    }

    class TestEligibilityStatsStore(reportTo: ActorRef) extends EligibilityStatsStore {
      def getEligibilityStats(): Future[List[EligibilityStats]] = {
        await((reportTo ? GetStats).mapTo[GetStatsResponse]).map(_.result)
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

  def replaceSpaces(s: String): String = s.replaceAllLiterally(" ", "-")

  "EligibilityStatsActor" when {
    "retrieving the stats from mongo store" must {

      "handle stats returned from mongo" in new TestApparatus {
        val gaugeNames = TestEligibilityStats.table.keys.toList.flatMap { reason ⇒
          TestEligibilityStats.table.values.flatMap(_.keys).toList.distinct.map { channel ⇒
            (reason, channel,
              s"backend.create-account.${replaceSpaces(reason)}.${replaceSpaces(channel)}"
            )
          }
        }

        actor ? GetStats

        eligibilityStatsStoreListener.expectMsg(GetStats)
        eligibilityStatsStoreListener.reply(GetStatsResponse(TestEligibilityStats.stats))

        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.CreateTableRequestReceived(TestEligibilityStats.stats))

        val gaugesRegistered = gaugeNames.map{ _ ⇒
          metricsListener.expectMsgType[MockMetrics.GaugeRegistered]
        }
        metricsListener.expectNoMsg()

        gaugesRegistered.foreach{
          case gaugeRegistered ⇒
            val (reason, channel, _) = gaugeNames.find(_._3 === gaugeRegistered.name).getOrElse(fail())
            gaugeRegistered.gauge.getValue shouldBe TestEligibilityStats.table.get(reason).flatMap(_.get(channel)).getOrElse(fail())

        }
        
        eligibilityStatsParserListener.expectMsg(TestEligibilityStatsParser.PrettyFormatTableRequestReceived(TestEligibilityStats.table))

      }

    }
  }
}

