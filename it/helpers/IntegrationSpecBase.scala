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

package helpers

import akka.http.scaladsl.model.HttpResponse
import helpers.WiremockHelper.stubPost
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.helptosave.repo.{MongoEnrolmentStore, MongoUserCapStore}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait IntegrationSpecBase
    extends AnyWordSpec
    with GivenWhenThen
    with TestSuite
    with ScalaFutures
    with IntegrationPatience
    with Matchers
    with WiremockHelper
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually
    with FutureAwaits
    with DefaultAwaitTimeout
    with EnrolmentStoreRepoHelper
    with UserCapStoreRepoHelper {

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int    = WiremockHelper.wiremockPort
  val mockUrl          = s"http://$mockHost:$mockPort"

  val timeout: Timeout   = Timeout(Span(5, Seconds))
  val interval: Interval = Interval(Span(100, Millis))

  val AUTHORISATION_TOKEN = "Bearer ab9e219d-0d9d-4a1d-90e5-f2a5c287668c"


  def config: Map[String, String] = Map(
    "application.router"                                      -> "testOnlyDoNotUseInAppConf.Routes",
    "auditing.consumer.baseUri.host"                          -> s"$mockHost",
    "auditing.consumer.baseUri.port"                          -> s"$mockPort",
    "microservice.services.auth.host"                         -> s"$mockHost",
    "microservice.services.auth.port"                         -> s"$mockPort",
    "microservice.services.help-to-save-proxy.host" -> s"$mockHost",
    "microservice.services.help-to-save-proxy.port" -> s"$mockPort",
    "microservice.services.des.host" -> s"$mockHost",
    "microservice.services.des.port" -> s"$mockPort",
    "microservice.services.itmp-enrolment.host" -> s"$mockHost",
    "microservice.services.itmp-enrolment.port" -> s"$mockPort",
    "microservice.services.itmp-eligibility-check.host" -> s"$mockHost",
    "microservice.services.itmp-eligibility-check.port" -> s"$mockPort",
    "microservice.services.itmp-threshold.host" -> s"$mockHost",
    "microservice.services.itmp-threshold.port" -> s"$mockPort",
    "nsi.create-account.version" -> "V2.0"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout  = scaled(Span(20, Seconds)),
    interval = scaled(Span(200, Millis))
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit lazy val hc: HeaderCarrier = HeaderCarrier(
    authorization = Some(Authorization("Bearer some-token"))
  )

  def statusOf(res: Future[HttpResponse])(implicit timeout: Duration): Int =
    Await.result(res, timeout).status.intValue()

  val enrolmentStoreRepository = app.injector.instanceOf[MongoEnrolmentStore]
  val userCapStoreRepository = app.injector.instanceOf[MongoUserCapStore]

  override def beforeEach(): Unit = {
    resetWiremock()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

  protected def stubAudit: Unit = stubPost(s"/write/audit", Status.OK)
}
