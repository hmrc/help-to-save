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

import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.helptosave.repo.{MongoEnrolmentStore, MongoUserCapStore}
import uk.gov.hmrc.helptosave.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
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
    with WireMockSupport
    with WireMockMethods
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually
    with FutureAwaits
    with DefaultAwaitTimeout
    with EnrolmentStoreRepoHelper
    with UserCapStoreRepoHelper {

  val mockUrl = s"http://$wireMockHost:$wireMockPort"

  val timeout: Timeout   = Timeout(Span(5, Seconds))
  val interval: Interval = Interval(Span(100, Millis))

  val AUTHORISATION_TOKEN = "Bearer ab9e219d-0d9d-4a1d-90e5-f2a5c287668c"

  def config: Map[String, String] = Map(
    "application.router"                                -> "testOnlyDoNotUseInAppConf.Routes",
    "auditing.consumer.baseUri.host"                    -> s"$wireMockHost",
    "auditing.consumer.baseUri.port"                    -> s"$wireMockPort",
    "microservice.services.auth.host"                   -> s"$wireMockHost",
    "microservice.services.auth.port"                   -> s"$wireMockPort",
    "microservice.services.help-to-save-proxy.host"     -> s"$wireMockHost",
    "microservice.services.help-to-save-proxy.port"     -> s"$wireMockPort",
    "microservice.services.des.host"                    -> s"$wireMockHost",
    "microservice.services.des.port"                    -> s"$wireMockPort",
    "microservice.services.itmp-enrolment.host"         -> s"$wireMockHost",
    "microservice.services.itmp-enrolment.port"         -> s"$wireMockPort",
    "microservice.services.itmp-eligibility-check.host" -> s"$wireMockHost",
    "microservice.services.itmp-eligibility-check.port" -> s"$wireMockPort",
    "microservice.services.itmp-threshold.host"         -> s"$wireMockHost",
    "microservice.services.itmp-threshold.port"         -> s"$wireMockPort",
    "nsi.create-account.version"                        -> "V2.0"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(20, Seconds)),
    interval = scaled(Span(200, Millis))
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build()

  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit lazy val hc: HeaderCarrier    = HeaderCarrier(
    authorization = Some(Authorization("Bearer some-token"))
  )

  def statusOf(res: Future[HttpResponse])(implicit timeout: Duration): Int =
    Await.result(res, timeout).status.intValue()

  val enrolmentStoreRepository: MongoEnrolmentStore = app.injector.instanceOf[MongoEnrolmentStore]
  val userCapStoreRepository: MongoUserCapStore     = app.injector.instanceOf[MongoUserCapStore]

  def buildRequest(path: String): WSRequest =
    ws.url(s"http://localhost:$port/help-to-save$path").withFollowRedirects(false)

  protected def stubAudit(): Unit = when(POST, "write/audit").thenReturn(Status.OK)
}
