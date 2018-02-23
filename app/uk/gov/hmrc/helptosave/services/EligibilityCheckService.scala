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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckEvent, EligibilityCheckResult, UCResponse}
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{Logging, NINO, LogMessageTransformer, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EligibilityCheckServiceImpl])
trait EligibilityCheckService {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]]

}

@Singleton
class EligibilityCheckServiceImpl @Inject() (helpToSaveProxyConnector:  HelpToSaveProxyConnector,
                                             eligibilityCheckConnector: EligibilityCheckConnector,
                                             configuration:             Configuration,
                                             auditor:                   HTSAuditor
)(implicit ninoLogMessageTransformer: LogMessageTransformer)
  extends EligibilityCheckService with Logging with ServicesConfig {

  private val isUCEnabled: Boolean = configuration.underlying.getBoolean("microservice.uc-enabled")

  logger.info(s"UniversalCredits checks enabled = $isUCEnabled")

  override def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]] = {
    val r: EitherT[Future, String, (Option[EligibilityCheckResult], Option[UCResponse])] =
      if (isUCEnabled) {
        for {
          ucResponse ← EitherT.liftT(getUCDetails(nino, UUID.randomUUID()))
          result ← eligibilityCheckConnector.isEligible(nino, ucResponse)
        } yield (result, ucResponse)
      } else {
        for {
          result ← eligibilityCheckConnector.isEligible(nino, None)
        } yield (result, None)
      }

    r.map {
      case (Some(ecR), ucR) ⇒
        auditor.sendEvent(EligibilityCheckEvent(nino, ecR, ucR), nino)
        Some(ecR)
      case _ ⇒ None
    }
  }

  private def getUCDetails(nino: NINO, txnId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[UCResponse]] =
    helpToSaveProxyConnector.ucClaimantCheck(nino, txnId)
      .fold({ e ⇒
        logger.warn(s"Error while retrieving UC details: $e", nino)
        None
      }, Some(_)
      )
}

