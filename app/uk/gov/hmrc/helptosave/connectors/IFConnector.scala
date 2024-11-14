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
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IFConnectorImpl])
trait IFConnector {
  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]]
}

@Singleton
class IFConnectorImpl @Inject()(http: HttpClientV2, servicesConfig: ServicesConfig)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends IFConnector with Logging {

  val payeURL: String = servicesConfig.baseUrl("if")
  val root: String = servicesConfig.getString("microservice.services.if.root")

  val originatorIdHeader: (String, String) = "Originator-Id" -> servicesConfig.getString(
    "microservice.services.paye-personal-details.originatorId")

  def payePersonalDetailsUrl(nino: String): URL = url"$payeURL$root/pay-as-you-earn/02.00.00/individuals/$nino"

  override def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    logger.info(s"[IFConnector][getPersonalDetails] GET request: " +
      s" header - ${appConfig.ifHeaders:+ originatorIdHeader}" +
      s" payePersonalDetailsUrl - ${payePersonalDetailsUrl(nino)}")

    http.get(payePersonalDetailsUrl(nino)).transform(_.addHttpHeaders(appConfig.ifHeaders:+ originatorIdHeader:_*))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

}
