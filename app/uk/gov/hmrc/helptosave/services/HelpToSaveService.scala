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

package uk.gov.hmrc.helptosave.services

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.apache.pekko.pattern.ask
import play.api.Configuration
import play.api.http.Status
import play.mvc.Http.Status.{FORBIDDEN, OK}
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.config.featureSwitches.FeatureSwitch.CallDES
import uk.gov.hmrc.helptosave.config.featureSwitches.FeatureSwitching
import uk.gov.hmrc.helptosave.connectors.{DESConnector, HelpToSaveProxyConnector, IFConnector}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.modules.ThresholdManagerProvider
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps.getApiCorrelationId
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.Time.nanosToPrettyString
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, NINO, PagerDutyAlerting, Result, maskNino, withTime}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

@ImplementedBy(classOf[HelpToSaveServiceImpl])
trait HelpToSaveService {

  def getEligibility(nino: NINO, path: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Result[EligibilityCheckResponse]

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayePersonalDetails]

}

@Singleton
class HelpToSaveServiceImpl @Inject()(
                                       helpToSaveProxyConnector: HelpToSaveProxyConnector,
                                       dESConnector: DESConnector,
                                       iFConnector: IFConnector,
                                       auditor: HTSAuditor,
                                       metrics: Metrics,
                                       pagerDutyAlerting: PagerDutyAlerting,
                                       ucThresholdProvider: ThresholdManagerProvider)(
                                       implicit ninoLogMessageTransformer: LogMessageTransformer,
                                       appConfig: AppConfig, val config: Configuration)
  extends HelpToSaveService with Logging with FeatureSwitching {

  override def getEligibility(nino: NINO, path: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Result[EligibilityCheckResponse] =
    for {
      threshold <- EitherT.liftF(getThresholdValue())
      ucResponse <- EitherT.liftF(getUCDetails(nino, UUID.randomUUID(), threshold))
      result <- getEligibility(nino, ucResponse)
    } yield {
      auditor.sendEvent(EligibilityCheckEvent(nino, result, ucResponse, path), nino)
      EligibilityCheckResponse(result, threshold)
    }

  private def getThresholdValue()(implicit ec: ExecutionContext): Future[Option[Double]] =
    ucThresholdProvider.thresholdManager
      .ask(GetThresholdValue)(appConfig.thresholdAskTimeout)
      .mapTo[GetThresholdValueResponse]
      .map(r => r.result)

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = {
    withTime(metrics.itmpSetFlagTimer)(dESConnector.setFlag(nino)).map {
      case (time, Success(response)) =>
        val additionalParams = Seq("DesCorrelationId" -> response.desCorrelationId, "apiCorrelationId" -> getApiCorrelationId())
        response.status match {
          case OK =>
            logger.info(
              s"DES/ITMP HtS flag setting returned status 200 (OK) (round-trip time: ${nanosToPrettyString(time)})",
              nino,
              additionalParams: _*)
            Right(())

          case FORBIDDEN =>
            metrics.itmpSetFlagConflictCounter.inc()
            logger.warn(
              s"Tried to set ITMP HtS flag even though it was already set, received status 403 (Forbidden) " +
                s"- proceeding as normal  (round-trip time: ${nanosToPrettyString(time)})",
              nino,
              additionalParams: _*
            )
            Right(())

          case other =>
            metrics.itmpSetFlagErrorCounter.inc()
            pagerDutyAlerting.alert("Received unexpected http status in response to setting ITMP flag")
            Left(s"Received unexpected response status ($other) when trying to set ITMP flag. Body was: " +
              s"${maskNino(response.body)} (round-trip time: ${nanosToPrettyString(time)})")
        }
      case (time, Failure(NonFatal(e))) =>
        metrics.itmpSetFlagErrorCounter.inc()
        pagerDutyAlerting.alert("Failed to make call to set ITMP flag")
        Left(s"Encountered unexpected error while trying to set the ITMP flag: ${e.getMessage} (round-trip time: ${nanosToPrettyString(time)})")
    }.pipe(EitherT(_))
  }

  def getPersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayePersonalDetails] =
    withTime(metrics.itmpSetFlagTimer) {
      if (isEnabled(CallDES)) dESConnector.getPersonalDetails(nino) else iFConnector.getPersonalDetails(nino)
    } map {
      case (time, Success(response)) =>
        val additionalParams =
          if (isEnabled(CallDES)) {
            "DesCorrelationId" -> response.desCorrelationId
          } else {
            "IfCorrelationId" -> response.ifCorrelationId
          }
        response.status match {
          case Status.OK =>
            response.parseJsonWithoutLoggingBody[PayePersonalDetails] tap (_.left.foreach { e =>
              metrics.payePersonalDetailsErrorCounter.inc()
              logger.warn(
                s"Could not parse JSON response from paye-personal-details, received 200 (OK): $e ${timeString(time)}",
                nino,
                additionalParams)
              pagerDutyAlerting.alert("Could not parse JSON in the paye-personal-details response")
            })

          case other =>
            logger.warn(
              s"Call to paye-personal-details unsuccessful. Received unexpected status $other ${timeString(time)}",
              nino,
              additionalParams)
            metrics.payePersonalDetailsErrorCounter.inc()
            pagerDutyAlerting.alert("Received unexpected http status in response to paye-personal-details")
            Left(s"Received unexpected status $other")
        }
      case (time, Failure(e)) =>
        pagerDutyAlerting.alert("Failed to make call to paye-personal-details")
        metrics.payePersonalDetailsErrorCounter.inc()
        Left(s"Call to paye-personal-details unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
    } pipe (EitherT(_))

  private def getEligibility(nino: NINO, ucResponse: Option[UCResponse])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Result[EligibilityCheckResult] =
    withTime(metrics.itmpSetFlagTimer)(dESConnector.isEligible(nino, ucResponse)).map {
      case (_, Success(response)) if response.status == OK =>
        response.parseJson[EligibilityCheckResult] tap (_.left.foreach { _ =>
          pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
        })
      case (time, Success(response)) =>
        val other = response.status
        val additionalParams = "DesCorrelationId" -> response.desCorrelationId
        logger.warn(s"Call to check eligibility unsuccessful. Received unexpected status $other ${timeString(time)}. " +
          s"Body was: ${response.body}",
          nino,
          additionalParams
        )
        pagerDutyAlerting.alert("Received unexpected http status in response to eligibility check")
        Left(s"Received unexpected status $other")
      case (time, Failure(response)) =>
        pagerDutyAlerting.alert("Failed to make call to check eligibility")
        Left(s"Call to check eligibility unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
    }.pipe(EitherT(_))

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  private def getUCDetails(nino: NINO, txnId: UUID, threshold: Option[Double])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[UCResponse]] =
    threshold match {
      case None =>
        logger.warn("call to uc claimant check will not be made as there is no threshold value present", nino)
        Future.successful(None)
      case Some(thresholdValue) =>
        helpToSaveProxyConnector.ucClaimantCheck(nino, txnId, thresholdValue)
          .leftMap(_.tap(e => logger.warn(s"Error while retrieving UC details: $e", nino)))
          .toOption.value
    }

}
