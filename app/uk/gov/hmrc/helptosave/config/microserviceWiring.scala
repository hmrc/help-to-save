/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.config

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Writes
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps._

import scala.concurrent.{ExecutionContext, Future}

object HtsAuditConnector extends AuditConnector with AppName {
  override lazy val auditingConfig: AuditingConfig = LoadAuditingConfig("auditing")
}

@Singleton
class HtsAuthConnector @Inject() (wsHttp: WSHttp) extends PlayAuthConnector with ServicesConfig {
  override lazy val serviceUrl: String = baseUrl("auth")

  override def http: WSHttp = wsHttp
}

@ImplementedBy(classOf[WSHttpExtension])
trait WSHttp
  extends HttpGet with WSGet
  with HttpPost with WSPost
  with HttpPut with WSPut {

  def get(url:     String,
          headers: Map[String, String] = Map.empty[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def put[A](url:     String,
             body:    A,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class WSHttpExtension extends WSHttp with HttpAuditing with ServicesConfig {

  val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  override val hooks: Seq[HttpHook] = NoneRequired

  override def auditConnector: AuditConnector = HtsAuditConnector

  override def appName: String = getString("appName")

  override def mapErrors(httpMethod: String, url: String, f: Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] = f

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
   * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def get(url:     String,
          headers: Map[String, String] = Map.empty[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.GET(url)(httpReads, hc.withExtraHeaders(headers), ec)

  def put[A](url:     String,
             body:    A,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.PUT(url, body)(w, httpReads, hc.withExtraHeaders(headers), ec)

}
