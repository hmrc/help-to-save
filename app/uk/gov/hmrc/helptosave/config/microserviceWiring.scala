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
import play.api.http.HttpVerbs.{GET ⇒ GET_VERB, POST ⇒ POST_VERB, PUT ⇒ PUT_VERB}
import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig

import scala.concurrent.ExecutionContext.Implicits.global
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

  def get(url: String)(implicit rhc: HeaderCarrier): Future[HttpResponse]

  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse]

  def put[A](url:     String,
             body:    A,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit rds: Writeable[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class WSHttpExtension extends WSHttp with HttpAuditing with ServicesConfig {

  override val hooks: Seq[HttpHook] = NoneRequired

  override def auditConnector: AuditConnector = HtsAuditConnector

  override def appName: String = getString("appName")

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
   * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def get(url: String)(implicit rhc: HeaderCarrier): Future[HttpResponse] = withTracing(GET_VERB, url) {
    val httpResponse = doGet(url)
    executeHooks(url, GET_VERB, None, httpResponse)
    httpResponse
  }

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
   * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = withTracing(POST_VERB, url) {
    val httpResponse = doPost(url, body, headers)
    executeHooks(url, POST_VERB, None, httpResponse)
    httpResponse
  }

  def put[A](url:     String,
             body:    A,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit rds: Writeable[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    // cannot use `doPut` over here because it doesn't allow for headers
    val httpResponse = buildRequest(url).withHeaders(headers.toList: _*).put(body).map(new WSHttpResponse(_))
    executeHooks(url, PUT_VERB, None, httpResponse)
    httpResponse
  }

}
