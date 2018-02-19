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

package uk.gov.hmrc.helptosave.models

import cats.instances.int._
import cats.syntax.eq._
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.config.AppName

trait HTSEvent {
  val value: DataEvent
}

object HTSEvent extends AppName {
  def apply(auditType: String,
            detail:    Map[String, String])(implicit hc: HeaderCarrier): DataEvent =
    DataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags("", "N/A"))

}

case class EligibilityCheckEvent(nino:              NINO,
                                 eligibilityResult: EligibilityCheckResult,
                                 ucResponse:        Option[UCResponse])(implicit hc: HeaderCarrier) extends HTSEvent {

  val value: DataEvent = {
    val details =
      if (eligibilityResult.resultCode === 1) {
        ucResponse match {
          case Some(ucR) ⇒ Map[String, String](
            "nino" → nino,
            "eligible" → "true",
            "isUCClaimant" → ucR.ucClaimant.toString,
            "isWithinUCThreshold" → ucR.withinThreshold.getOrElse(false).toString
          )

          case _ ⇒ Map[String, String]("nino" → nino, "eligible" → "true")
        }
      } else {

        val reason = "Response: " +
          s"resultCode=${eligibilityResult.resultCode}, reasonCode=${eligibilityResult.reasonCode}, " +
          s"meaning result='${eligibilityResult.result}', reason='${eligibilityResult.reason}'"

        ucResponse match {
          case Some(ucR) ⇒ Map[String, String](
            "nino" → nino,
            "eligible" → "false",
            "isUCClaimant" → ucR.ucClaimant.toString,
            "isWithinUCThreshold" → ucR.withinThreshold.getOrElse(false).toString,
            "reason" -> reason
          )

          case _ ⇒
            Map[String, String]("nino" → nino, "eligible" → "false", "reason" -> reason)
        }
      }

    HTSEvent("EligibilityResult", details)
  }
}
