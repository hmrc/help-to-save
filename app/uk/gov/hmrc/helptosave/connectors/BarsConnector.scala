/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.http.HttpClient._
import uk.gov.hmrc.helptosave.models.BankDetailsValidationRequest
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BarsConnectorImpl])
trait BarsConnector {

  def validate(request: BankDetailsValidationRequest, trackingId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class BarsConnectorImpl @Inject() (http: HttpClient)(implicit appConfig: AppConfig) extends BarsConnector with Logging {

  import uk.gov.hmrc.helptosave.connectors.BarsConnectorImpl._

  private val barsEndpoint: String = s"${appConfig.barsUrl}/validate/bank-details"

  private val headers = Map("Content-Type" -> "application/json")

  override def validate(request: BankDetailsValidationRequest, trackingId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(barsEndpoint, bodyJson(request), headers.+("X-Tracking-Id" -> trackingId.toString))

  private def bodyJson(request: BankDetailsValidationRequest) = Json.toJson(BarsRequest(Account(request.sortCode, request.accountNumber)))
}

object BarsConnectorImpl {

  private case class Account(sortCode: String, accountNumber: String)
  private case class BarsRequest(account: Account)

  private implicit val accountFormat: Format[Account] = Json.format[Account]
  private implicit val barsRequestFormat: Format[BarsRequest] = Json.format[BarsRequest]

}
