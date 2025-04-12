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
import play.api.libs.json.{JsNull, JsValue}
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.models.UCResponse
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.libs.ws.writeableOf_JsValue

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DESConnectorImpl])
trait DESConnector {
  def isEligible(nino: String, ucResponse: Option[UCResponse])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]]

  def setFlag(
    nino: NINO
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]]

  def getPersonalDetails(
    nino: NINO
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]]

  def getThreshold()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]]
}

@Singleton
class DESConnectorImpl @Inject() (http: HttpClientV2, servicesConfig: ServicesConfig)(implicit appConfig: AppConfig)
    extends DESConnector
    with Logging {

  val itmpECBaseURL: String    = servicesConfig.baseUrl("itmp-eligibility-check")
  val itmpEnrolmentURL: String = servicesConfig.baseUrl("itmp-enrolment")
  val payeURL: String          = servicesConfig.baseUrl("paye-personal-details")
  val itmpThresholdURL: URL    = url"${servicesConfig.baseUrl("itmp-threshold")}/universal-credits/threshold-amount"

  implicit val booleanShow: Show[Boolean] = Show.show(if _ then "Y" else "N")

  val body: JsValue = JsNull

  val originatorIdHeader: (String, String) =
    "Originator-Id" -> servicesConfig.getString("microservice.services.paye-personal-details.originatorId")

  def eligibilityCheckUrl(nino: String): URL = url"$itmpECBaseURL/help-to-save/eligibility-check/$nino"

  def eligibilityCheckQueryParameters(ucResponse: Option[UCResponse]): Seq[(String, String)] =
    ucResponse match {
      case Some(UCResponse(a, Some(b))) => Seq("universalCreditClaimant" -> a.show, "withinThreshold" -> b.show)
      case Some(UCResponse(a, None))    => Seq("universalCreditClaimant" -> a.show)
      case _                            => Seq()
    }

  def setFlagUrl(nino: NINO): URL = url"$itmpEnrolmentURL/help-to-save/accounts/$nino"

  def payePersonalDetailsUrl(nino: String): URL = url"$payeURL/pay-as-you-earn/02.00.00/individuals/$nino"

  override def isEligible(nino: String, ucResponse: Option[UCResponse] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    logger.info(
      s"[DESConnector][isEligible] GET request: " +
        s" header - ${appConfig.desHeaders}" +
        s" eligibilityCheckUrl - ${eligibilityCheckUrl(nino)}"
    )

    http
      .get(eligibilityCheckUrl(nino))(hc.copy(authorization = None))
      .transform(
        _.withQueryStringParameters(eligibilityCheckQueryParameters(ucResponse)*)
          .addHttpHeaders(appConfig.desHeaders*)
      )
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  override def setFlag(
    nino: NINO
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    logger.info(
      s"[DESConnector][setFlag] PUT request: " +
        s" header - ${appConfig.desHeaders} " +
        s" setFlagUrl - ${setFlagUrl(nino)}"
    )
    http
      .put(setFlagUrl(nino))(hc.copy(authorization = None))
      .transform(_.addHttpHeaders(appConfig.desHeaders*))
      .withBody(body)
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  override def getPersonalDetails(
    nino: NINO
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    logger.info(
      s"[DESConnector][getPersonalDetails] GET request: " +
        s" header - ${appConfig.desHeaders :+ originatorIdHeader}" +
        s" payePersonalDetailsUrl - ${payePersonalDetailsUrl(nino)}"
    )
    http
      .get(payePersonalDetailsUrl(nino))(hc.copy(authorization = None))
      .transform(_.addHttpHeaders(appConfig.desHeaders :+ originatorIdHeader*))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  override def getThreshold()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    logger.info(
      s"[DESConnector][getThreshold] GET request: " +
        s"itmpThresholdURL - $itmpThresholdURL" +
        s" header - ${appConfig.desHeaders :+ originatorIdHeader}"
    )
    http
      .get(itmpThresholdURL)(hc.copy(authorization = None))
      .transform(_.addHttpHeaders(appConfig.desHeaders*))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

}
