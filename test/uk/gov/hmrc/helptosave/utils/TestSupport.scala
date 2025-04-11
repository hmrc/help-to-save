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

package uk.gov.hmrc.helptosave.utils

import com.codahale.metrics.{Counter, NoopMetricRegistry}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.ControllerComponents
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, LogMessageTransformerImpl, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.ExecutionContext

trait TestSupport extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach {
  lazy val additionalConfig: Configuration = Configuration()
  val originatorIdHeaderValue = "test-originator"

  def buildFakeApplication(extraConfig: Configuration): Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString(s"""
                                      | metrics.jvm = false
                                      | metrics.enabled = true
                                      | mongo-async-driver.org.apache.pekko.loglevel = ERROR
                                      | uc-threshold.ask-timeout = 10 seconds
                                      | play.modules.disabled = [ "play.api.mvc.CookiesModule" ]
                                      | microservice {
                                      |   services {
                                      |     paye-personal-details {
                                      |       originatorId = $originatorIdHeaderValue
                                      |     }
                                      |   }
                                      | }
            """.stripMargin)
        ).withFallback(extraConfig))
      .build()

  lazy val fakeApplication: Application = buildFakeApplication(additionalConfig)

  override protected def beforeAll(): Unit = {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit lazy val configuration: Configuration = fakeApplication.injector.instanceOf[Configuration]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val testCC: ControllerComponents = fakeApplication.injector.instanceOf[ControllerComponents]

  val servicesConfig: ServicesConfig = fakeApplication.injector.instanceOf[ServicesConfig]

  val mockMetrics: Metrics = new Metrics(new NoopMetricRegistry()) {
    override def counter(name: String): Counter = new Counter()
  }

  private val hmrcGenerator: Generator = new Generator()

  val startDate: LocalDate = LocalDate.of(1800, 1, 1) // scalastyle:ignore magic.number
  val endDate: LocalDate = LocalDate.of(2000, 1, 1) // scalastyle:ignore magic.number

  def randomNINO(): String = hmrcGenerator.nextNino.value

  implicit lazy val transformer: LogMessageTransformer = new LogMessageTransformerImpl(configuration)

  implicit lazy val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]

  val nsiAccountJson: JsObject = Json.parse("""
                                    |{
                                    |  "accountNumber": "AC01",
                                    |  "accountBalance": "200.34",
                                    |  "accountClosedFlag": "",
                                    |  "accountBlockingCode": "00",
                                    |  "clientBlockingCode": "00",
                                    |  "currentInvestmentMonth": {
                                    |    "investmentRemaining": "15.50",
                                    |    "investmentLimit": "50.00",
                                    |    "endDate": "2018-02-28"
                                    |  },
                                    |  "clientForename":"Testforename",
                                    |  "clientSurname":"Testsurname",
                                    |  "emailAddress":"test@example.com",
                                    |  "terms": [
                                    |     {
                                    |       "termNumber":2,
                                    |       "startDate":"2020-01-01",
                                    |       "endDate":"2021-12-31",
                                    |       "bonusEstimate":"67.00",
                                    |       "bonusPaid":"0.00"
                                    |    },
                                    |    {
                                    |       "termNumber":1,
                                    |       "startDate":"2018-01-01",
                                    |       "endDate":"2019-12-31",
                                    |       "bonusEstimate":"123.45",
                                    |       "bonusPaid":"123.45"
                                    |    }
                                    |  ]
                                    |}
    """.stripMargin).as[JsObject]
}
