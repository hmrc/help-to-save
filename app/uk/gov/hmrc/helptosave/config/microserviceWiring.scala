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

package uk.gov.hmrc.helptosave

import play.api.libs.json.Writes
import play.api.http.HttpVerbs.{GET ⇒ GET_VERB, POST ⇒ POST_VERB}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws.{WSHttp ⇒ _, _}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}


object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

object MicroserviceAuthConnector extends AuthConnector with ServicesConfig {
  override val authBaseUrl = baseUrl("auth")
}


class WSHttp extends WSGet with WSPut with WSPost with WSDelete with WSPatch with AppName {
  override val hooks: Seq[HttpHook] = NoneRequired

  /**
    * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
    * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
    */
  def get(url: String, headers: Map[String,String] = Map.empty[String,String])(
           implicit rhc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = withTracing(GET_VERB, url) {
    val httpResponse = buildRequest(url).withHeaders(headers.toSeq: _*).get().map(new WSHttpResponse(_))
    executeHooks(url, GET_VERB, None, httpResponse)
    httpResponse
  }

  /**
    * Returns a [[Future[HttpResponse]] without throwing exceptions if the status is not `2xx`. Needed
    * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
    */
  def post[A](url: String,
              body: A,
              headers: Seq[(String, String)]
             )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = withTracing(POST_VERB, url) {
    val httpResponse = doPost(url, body, headers)
    executeHooks(url, POST_VERB, None, httpResponse)
    httpResponse
  }

  def postForm(url: String, body: Map[String,Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val httpResponse = doFormPost(url, body)
    executeHooks(url, POST_VERB, None, httpResponse)
    httpResponse
  }

}

class WSHttpProxy extends WSHttp with WSProxy with RunMode with HttpAuditing with ServicesConfig {
  override lazy val appName = getString("appName")
  override lazy val wsProxyServer = WSProxyConfiguration(s"proxy")
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector = MicroserviceAuditConnector
}

