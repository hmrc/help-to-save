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

package uk.gov.hmrc.helptosave.connectors

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.models.BankDetailsValidationRequest
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import java.net.URL

@ImplementedBy(classOf[BarsConnectorImpl])
trait BarsConnector {

  def validate(request: BankDetailsValidationRequest, trackingId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]]
}

@Singleton
class BarsConnectorImpl @Inject()(http: HttpClientV2)(implicit appConfig: AppConfig) extends BarsConnector with Logging {

  import uk.gov.hmrc.helptosave.connectors.BarsConnectorImpl._

  private val barsEndpoint: URL = url"${appConfig.barsUrl}/validate/bank-details"

  private val headers:(String, String) = "Content-Type" -> "application/json"

  override def validate(request: BankDetailsValidationRequest, trackingId: UUID)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    http.post(barsEndpoint)
      .transform(_
        .addHttpHeaders(headers, "X-Tracking-Id" -> trackingId.toString))
      .withBody(bodyJson(request))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  private def bodyJson(request: BankDetailsValidationRequest) =
    Json.toJson(BarsRequest(Account(request.sortCode, request.accountNumber)))
}

object BarsConnectorImpl {

  private case class Account(sortCode: String, accountNumber: String)
  private case class BarsRequest(account: Account)

  private implicit val accountFormat: Format[Account] = Json.format[Account]
  private implicit val barsRequestFormat: Format[BarsRequest] = Json.format[BarsRequest]

}
