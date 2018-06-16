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

package uk.gov.hmrc.helptosave.modules

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.ActorTestSupport
import uk.gov.hmrc.helptosave.actors.EligibilityStatsActor.GetStats
import uk.gov.hmrc.helptosave.services.EligibilityStatsService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityStatsModuleSpec extends ActorTestSupport("EligibilityStatsProviderSpec") with Eventually {

  val service = mock[EligibilityStatsService]

  implicit val timeout: Timeout = Timeout(10.seconds)

  def testConfiguration(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |eligibility-stats {
       |    enabled = $enabled
       |    timezone   = "Europe/London"
       |    trigger-time       = "01:00"
       |}
    """.stripMargin
  ))

  def mockEligibilityStatsService(result: Either[String, String]) =
    (service.getEligibilityStats()(_: ExecutionContext)).expects(*)
      .returning(Future.successful(result))

  "The EligibilityStatsProvider" should {
    "start up an instance of the EligibilityStatsActor correctly" in {
      mockEligibilityStatsService(Right("actual stats table"))

      val provider = new EligibilityStatsProviderImpl(system, testConfiguration(enabled = true), service)

      eventually(PatienceConfiguration.Timeout(10.seconds), PatienceConfiguration.Interval(10.second)) {
        val result = provider.esActor ? GetStats
        Await.result(result, 1.second) shouldBe "hello world"
      }

      //sleep here to ensure the service's mock getEligibilityStats() method
      // definitely gets called since it happens asynchronously
      Thread.sleep(1000L)
    }

    "not start up an instance of the UCThresholdManager if not enabled" in {
      val provider = new EligibilityStatsProviderImpl(system, testConfiguration(enabled = false), service)
      provider.esActor shouldBe ActorRef.noSender
    }

  }
}

