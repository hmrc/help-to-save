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

package uk.gov.hmrc.helptosave.utils

import java.time.LocalDate

import com.codahale.metrics.{Counter, Gauge, Timer}
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import com.typesafe.config.ConfigFactory
import uk.gov.hmrc.smartstub._
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, LogMessageTransformerImpl}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

trait TestSupport extends WordSpecLike with Matchers with MockFactory with UnitSpec with BeforeAndAfterAll {

  lazy val additionalConfig = Configuration()

  def buildFakeApplication(additionalConfig: Configuration): Application = {
    new GuiceApplicationBuilder()
      .configure(Configuration(
        ConfigFactory.parseString(
          """
            | metrics.enabled       = false
            | play.modules.disabled = [ "uk.gov.hmrc.helptosave.modules.UCThresholdModule", "uk.gov.hmrc.helptosave.modules.EligibilityStatsModule", "play.modules.reactivemongo.ReactiveMongoHmrcModule", "uk.gov.hmrc.helptosave.modules.EmailDeletionModule" ]
          """.stripMargin)
      ) ++ additionalConfig)
      .build()
  }

  lazy val fakeApplication: Application = buildFakeApplication(additionalConfig)

  override def beforeAll() {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override def afterAll() {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit lazy val configuration: Configuration = fakeApplication.injector.instanceOf[Configuration]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()

    override def registerIntGauge(name: String, gauge: Gauge[Int]): Gauge[Int] = gauge

  }

  private val hmrcGenerator: Generator = new Generator()

  val startDate: LocalDate = LocalDate.of(1800, 1, 1) // scalastyle:ignore magic.number
  val endDate: LocalDate = LocalDate.of(2000, 1, 1) // scalastyle:ignore magic.number
  val dateGen: Gen[LocalDate] = Gen.date(startDate, endDate)

  def randomDate(): LocalDate = dateGen.sample.getOrElse(sys.error("Could not generate date"))

  def randomNINO(): String = hmrcGenerator.nextNino.value

  implicit lazy val transformer: LogMessageTransformer = new LogMessageTransformerImpl(configuration)

  implicit lazy val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]
}

