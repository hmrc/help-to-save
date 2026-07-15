/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.models.{HIPEligibilityCheckRequest, UCResponse}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HIPEligibilityConnectorImpl])
trait HIPEligibilityConnector {
  def checkEligibility(nino: NINO, ucResponse: Option[UCResponse])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]]
}

@Singleton
class HIPEligibilityConnectorImpl @Inject() (http: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  appConfig: AppConfig
) extends HIPEligibilityConnector {

  private val hipBaseUrl: String = servicesConfig.baseUrl("hip")
  private val root: String       = servicesConfig.getString("microservice.services.hip.root")

  def eligibilityCheckUrl(nino: NINO): URL = url"${hipBaseUrl + root}/help-to-save/$nino/account"

  override def checkEligibility(nino: NINO, ucResponse: Option[UCResponse])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    val requestBody = HIPEligibilityCheckRequest(ucResponse)
    val headers     = appConfig.hipHeaders :+ ("correlationId" -> UUID.randomUUID().toString)

    http
      .post(eligibilityCheckUrl(nino))(using hc.copy(authorization = None))
      .transform(_.addHttpHeaders(headers*))
      .withBody(Json.toJson(requestBody))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }
}
