/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.helptosave.models.UCResponse
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, NINO}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.helptosave.http.HttpClient.HttpClientOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DESConnectorImpl])
trait DESConnector {
  def desCorrelationId(response: HttpResponse): String = response.header("CorrelationId").getOrElse("-")

  def isEligible(nino: String, ucResponse: Option[UCResponse])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def getThreshold()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class DESConnectorImpl @Inject() (http:           HttpClient,
                                  servicesConfig: ServicesConfig)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
  extends DESConnector with Logging {

  val itmpECBaseURL: String = servicesConfig.baseUrl("itmp-eligibility-check")
  val itmpEnrolmentURL: String = servicesConfig.baseUrl("itmp-enrolment")
  val payeURL: String = servicesConfig.baseUrl("paye-personal-details")
  val itmpThresholdURL: String = s"${servicesConfig.baseUrl("itmp-threshold")}/universal-credits/threshold-amount"

  implicit val booleanShow: Show[Boolean] = Show.show(if (_) "Y" else "N")

  val body: JsValue = JsNull

  val originatorIdHeader: (String, String) = "Originator-Id" → servicesConfig.getString("microservice.services.paye-personal-details.originatorId")

  def eligibilityCheckUrl(nino: String): String = s"$itmpECBaseURL/help-to-save/eligibility-check/$nino"

  def eligibilityCheckQueryParameters(ucResponse: Option[UCResponse]): Map[String, String] =
    ucResponse match {
      case Some(UCResponse(a, Some(b))) ⇒ Map("universalCreditClaimant" → a.show, "withinThreshold" -> b.show)
      case Some(UCResponse(a, None))    ⇒ Map("universalCreditClaimant" -> a.show)
      case _                            ⇒ Map()
    }

  def setFlagUrl(nino: NINO): String = s"$itmpEnrolmentURL/help-to-save/accounts/$nino"

  def payePersonalDetailsUrl(nino: String): String = s"$payeURL/pay-as-you-earn/02.00.00/individuals/$nino"

  override def isEligible(nino: String, ucResponse: Option[UCResponse] = None)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(eligibilityCheckUrl(nino), eligibilityCheckQueryParameters(ucResponse), appConfig.desHeaders)(hc.copy(authorization = None), ec)

  override def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.put(setFlagUrl(nino), body, appConfig.desHeaders)(Writes.JsValueWrites, hc.copy(authorization = None), ec)

  override def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(payePersonalDetailsUrl(nino), headers = appConfig.desHeaders + originatorIdHeader)(hc.copy(authorization = None), ec)

  override def getThreshold()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.get(itmpThresholdURL, headers = appConfig.desHeaders)(hc.copy(authorization = None), ec)

}
