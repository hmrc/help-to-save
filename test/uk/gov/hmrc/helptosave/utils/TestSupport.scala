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
import java.util.UUID

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.{Metrics ⇒ PlayMetrics}
import hmrc.smartstub._
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

trait TestSupport extends WordSpecLike with Matchers with MockFactory with UnitSpec with BeforeAndAfterAll {

  lazy val additionalConfig = Configuration()

  lazy val fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(Configuration("metrics.enabled" → false) ++ additionalConfig)
      .build()

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

  val mockHttp: WSHttp = mock[WSHttp]

  val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

  private val hmrcGenerator: Generator = new Generator()

  val startDate: LocalDate = LocalDate.of(1800, 1, 1) // scalastyle:ignore magic.number
  val endDate: LocalDate = LocalDate.of(2000, 1, 1) // scalastyle:ignore magic.number
  val dateGen: Gen[LocalDate] = Gen.date(startDate, endDate)

  def randomDate(): LocalDate = dateGen.sample.getOrElse(sys.error("Could not generate date"))

  def randomNINO(): String = hmrcGenerator.nextNino.value

  implicit val transformer: LogMessageTransformer = new LogMessageTransformer {
    override def transform(message: String, nino: NINO, correlationId: Option[UUID] = None): String = s"$nino - $message - $correlationId"
  }

}

