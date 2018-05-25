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

package uk.gov.hmrc.helptosave.services

import java.util.UUID

import akka.util.Timeout
import cats.data.EitherT
import cats.syntax.eq._
import cats.syntax.either._
import cats.instances.int._
import cats.instances.future._
import configs.syntax._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckEvent, EligibilityCheckResult, UCResponse}
import uk.gov.hmrc.helptosave.modules.ThresholdManagerProvider
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, NINO, Result}
import uk.gov.hmrc.http.HeaderCarrier
import akka.pattern.ask
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}
import uk.gov.hmrc.helptosave.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EligibilityCheckServiceImpl])
trait EligibilityCheckService {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult]

  def getThresholdValue()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Double]

}

@Singleton
class EligibilityCheckServiceImpl @Inject() (helpToSaveProxyConnector:  HelpToSaveProxyConnector,
                                             eligibilityCheckConnector: EligibilityCheckConnector,
                                             auditor:                   HTSAuditor,
                                             thresholdManagerProvider:  ThresholdManagerProvider)(implicit ninoLogMessageTransformer: LogMessageTransformer,
                                                                                                  appConfig: AppConfig)
  extends EligibilityCheckService with Logging {

  override def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] =
    for {
      threshold ← getThresholdValue()
      ucResponse ← EitherT.liftF(getUCDetails(nino, UUID.randomUUID(), threshold))
      result ← getEligibility(nino, ucResponse)
    } yield {
      auditor.sendEvent(EligibilityCheckEvent(nino, result, ucResponse), nino)
      result
    }

  private def getEligibility(nino: NINO, ucResponse: Option[UCResponse])(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] =
    eligibilityCheckConnector.isEligible(nino, ucResponse).subflatMap { result ⇒
      if (result.resultCode === 4) {
        Left(s"Received result code 4 from DES eligibility check interface. Result was '${result.result}'. Reason was '${result.reason}'")
      } else {
        Right(result)
      }
    }

  private def getUCDetails(nino: NINO, txnId: UUID, threshold: Double)(implicit hc: HeaderCarrier,
                                                                       ec: ExecutionContext): Future[Option[UCResponse]] =
    helpToSaveProxyConnector.ucClaimantCheck(nino, txnId, threshold)
      .fold({ e ⇒
        logger.warn(s"Error while retrieving UC details: $e", nino)
        None
      }, Some(_)
      )

  def getThresholdValue()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Double] = {
    if (appConfig.isUCThresholdEnabled) {
      EitherT(
        thresholdManagerProvider.thresholdManager.ask(GetThresholdValue)(appConfig.thresholdAskTimeout)
          .mapTo[GetThresholdValueResponse]
          .map(r ⇒ Either.fromOption(r.result, "Could not get threshold value")))
    } else {
      EitherT.pure[Future, String](appConfig.thresholdAmount)
    }
  }

}

