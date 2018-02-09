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

import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.models.EligibilityCheckResult
import uk.gov.hmrc.helptosave.util.{Logging, NINO, Result, base64Encode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[EligibilityCheckServiceImpl])
trait EligibilityCheckService {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]]

}

@Singleton
class EligibilityCheckServiceImpl @Inject() (helpToSaveProxyConnector:  HelpToSaveProxyConnector,
                                             eligibilityCheckConnector: EligibilityCheckConnector,
                                             configuration:             Configuration)
  extends EligibilityCheckService with Logging with ServicesConfig {

  private val isUCEnabled: Boolean = configuration.underlying.getBoolean("microservice.uc-enabled")

  override def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[EligibilityCheckResult]] = {

    if (isUCEnabled) {
      val txnId = UUID.randomUUID()
      for {
        ucResponse ← helpToSaveProxyConnector.ucClaimantCheck(new String(base64Encode(nino)), txnId)
        result ← eligibilityCheckConnector.isEligible(nino, Some(ucResponse))
      } yield result
    } else {
      eligibilityCheckConnector.isEligible(nino, None)
    }
  }
}

