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
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.controllers.routes
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.DataEvent

trait HTSEvent {
  val value: DataEvent
}

object HTSEvent {
  def apply(appName:         String,
            auditType:       String,
            detail:          Map[String, String],
            transactionName: String,
            path:            String)(implicit hc: HeaderCarrier): DataEvent =
    DataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags(transactionName, path))
}

case class EligibilityCheckEvent(nino:              NINO,
                                 eligibilityResult: EligibilityCheckResult,
                                 ucResponse:        Option[UCResponse],
                                 path:              String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {

  val value: DataEvent = {
    val details = {
      val result =
        if (eligibilityResult.resultCode === 1) {
          Map[String, String]("nino" → nino, "eligible" → "true")
        } else {
          val reason = "Response: " +
            s"resultCode=${eligibilityResult.resultCode}, reasonCode=${eligibilityResult.reasonCode}, " +
            s"meaning result='${eligibilityResult.result}', reason='${eligibilityResult.reason}'"

          Map[String, String]("nino" → nino, "eligible" → "false", "reason" -> reason)

        }
      result ++ ucData(ucResponse)
    }

    HTSEvent(appConfig.appName, "EligibilityResult", details, "eligibility-result", path)
  }

  def ucData(ucResponse: Option[UCResponse]): Map[String, String] = ucResponse match {
    case Some(UCResponse(isClaimant, Some(withinThreshold))) ⇒
      Map("isUCClaimant" -> isClaimant.toString, "isWithinUCThreshold" -> withinThreshold.toString)
    case Some(UCResponse(isClaimant, None)) ⇒ Map("isUCClaimant" -> isClaimant.toString)
    case None                               ⇒ Map.empty[String, String]
  }
}

case class AccountCreated(userInfo: NSIPayload, source: String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {

  private val createAccountURL = routes.HelpToSaveController.createAccount().url

  val value: DataEvent = HTSEvent(
    appConfig.appName,
    "AccountCreated",
    Map[String, String](
      "forename" → userInfo.forename,
      "surname" → userInfo.surname,
      "dateOfBirth" → userInfo.dateOfBirth.toString,
      "nino" → userInfo.nino,
      "address1" → userInfo.contactDetails.address1,
      "address2" → userInfo.contactDetails.address2,
      "address3" → userInfo.contactDetails.address3.getOrElse(""),
      "address4" → userInfo.contactDetails.address4.getOrElse(""),
      "address5" → userInfo.contactDetails.address5.getOrElse(""),
      "postcode" → userInfo.contactDetails.postcode,
      "countryCode" → userInfo.contactDetails.countryCode.getOrElse(""),
      "email" → userInfo.contactDetails.email.getOrElse(""),
      "phoneNumber" → userInfo.contactDetails.phoneNumber.getOrElse(""),
      "communicationPreference" → userInfo.contactDetails.communicationPreference,
      "registrationChannel" → userInfo.registrationChannel,
      "source" → source
    ),
    "account-created",
    createAccountURL
  )
}

