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

import cats.Show
import cats.syntax.show._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsNull, JsValue, Writes}
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosave.models.UCResponse
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IFConnectorImpl])
trait IFConnector {
  def ifCorrelationId(response: HttpResponse): String = response.header("CorrelationId").getOrElse("-")

  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class IFConnectorImpl @Inject()(http: HttpClient, servicesConfig: ServicesConfig)(implicit appConfig: AppConfig)
    extends IFConnector with Logging {

  val payeURL: String = servicesConfig.baseUrl("paye-personal-details-if")
  val root: String = servicesConfig.getString("microservice.services.paye-personal-details-if.root")

  implicit val booleanShow: Show[Boolean] = Show.show(if (_) "Y" else "N")

  val originatorIdHeader: (String, String) = "Originator-Id" -> servicesConfig.getString(
    "microservice.services.paye-personal-details.originatorId")

  def payePersonalDetailsUrl(nino: String): String = s"$payeURL$root/pay-as-you-earn/02.00.00/individuals/$nino"

  override def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(payePersonalDetailsUrl(nino), headers = appConfig.ifHeaders + originatorIdHeader)(
      hc.copy(authorization = None),
      ec)

}
