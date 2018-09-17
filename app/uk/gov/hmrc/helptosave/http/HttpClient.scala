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

package uk.gov.hmrc.helptosave.http

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HttpVerbs
import play.api.libs.json.Writes
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HttpClient @Inject() (override val auditConnector: AuditConnector,
                            config:                      Configuration,
                            wsClient:                    WSClient)

  extends DefaultHttpClient(config, auditConnector, wsClient) with HttpVerbs {

  override val hooks: Seq[HttpHook] = NoneRequired

  private class RawHttpReads extends HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  // this HttpReads instance for HttpResponse is preferred over the default
  // uk.gov.hmrc.http.RawReads.readRaw as this custom one doesn't throw exceptions
  private val rawHttpReads = new RawHttpReads

  def get(url:         String,
          queryParams: Map[String, String] = Map.empty[String, String],
          headers:     Map[String, String] = Map.empty[String, String]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    GET(url, queryParams.toSeq)(rawHttpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)

  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    POST(url, body, headers.toSeq)(w, rawHttpReads, hc, ec)

  def put[A](url:     String,
             body:    A,
             headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    PUT(url, body)(w, rawHttpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)
}
