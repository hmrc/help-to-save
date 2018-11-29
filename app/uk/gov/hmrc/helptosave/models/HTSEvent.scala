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
import play.api.libs.json._
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.controllers.routes
import uk.gov.hmrc.helptosave.models.AccountCreated.{AllDetails, PrePopulatedUserData, ManuallyEnteredDetails}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

trait HTSEvent {
  val value: ExtendedDataEvent
}

object HTSEvent {
  def apply(appName:         String,
            auditType:       String,
            detail:          JsValue,
            transactionName: String,
            path:            String)(implicit hc: HeaderCarrier): ExtendedDataEvent =
    ExtendedDataEvent(appName, auditType = auditType, detail = detail, tags = hc.toAuditTags(transactionName, path))
}

case class EligibilityCheckEvent(nino:              NINO,
                                 eligibilityResult: EligibilityCheckResult,
                                 ucResponse:        Option[UCResponse],
                                 path:              String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {

  val value: ExtendedDataEvent = {

    HTSEvent(appConfig.appName, "EligibilityResult", EligibilityResult(nino, eligibilityResult, ucResponse), "eligibility-result", path)
  }

}

case class EligibilityResult(nino:                String,
                             eligible:            Boolean,
                             ineligibleReason:    Option[EligibilityCheckResult] = None,
                             isUCClaimant:        Option[Boolean]                = None,
                             isWithinUCThreshold: Option[Boolean]                = None)

object EligibilityResult {

  implicit val format: Format[EligibilityResult] = Json.format[EligibilityResult]

  def apply(nino: String, eligibilityResult: EligibilityCheckResult, ucResponse: Option[UCResponse]): JsValue = {

    val details =
      if (eligibilityResult.resultCode === 1) {
        EligibilityResult(nino, true, isUCClaimant = ucResponse.map(_.ucClaimant), isWithinUCThreshold = ucResponse.flatMap(_.withinThreshold))
      } else {
        EligibilityResult(nino, false, Some(eligibilityResult), ucResponse.map(_.ucClaimant), ucResponse.flatMap(_.withinThreshold))
      }

    Json.toJson(details)
  }
}

case class AccountCreated(userInfo: NSIPayload, source: String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {

  private val createAccountURL = routes.HelpToSaveController.createAccount().url

  val value: ExtendedDataEvent = HTSEvent(
    appConfig.appName,
    "AccountCreated",
    Json.toJson(AllDetails(PrePopulatedUserData(
      userInfo.forename,
      userInfo.surname,
      userInfo.dateOfBirth.toString,
      userInfo.nino,
      userInfo.contactDetails.address1,
      userInfo.contactDetails.address2,
      userInfo.contactDetails.address3.getOrElse(""),
      userInfo.contactDetails.address4.getOrElse(""),
      userInfo.contactDetails.address5.getOrElse(""),
      userInfo.contactDetails.postcode,
      userInfo.contactDetails.countryCode.getOrElse(""),
      userInfo.contactDetails.email.getOrElse(""),
      userInfo.contactDetails.phoneNumber.getOrElse(""),
      userInfo.contactDetails.communicationPreference,
      userInfo.registrationChannel,
      source
    ),
                           userInfo.nbaDetails.map{ bankDetails ⇒
        ManuallyEnteredDetails(
          bankDetails.accountName,
          bankDetails.accountNumber,
          bankDetails.sortCode,
          bankDetails.rollNumber.getOrElse("")
        )
      }
    )),
    "account-created",
    createAccountURL
  )
}
object AccountCreated {

  case class AllDetails(prePopulatedUserData: PrePopulatedUserData, manuallyEnteredDetail: Option[ManuallyEnteredDetails])

  object AllDetails {

    implicit val format: Format[AllDetails] = Json.format[AllDetails]
  }

  case class PrePopulatedUserData(forename:                String,
                                  surname:                 String,
                                  dateOfBirth:             String,
                                  nino:                    String,
                                  address1:                String,
                                  address2:                String,
                                  address3:                String,
                                  address4:                String,
                                  address5:                String,
                                  postcode:                String,
                                  countryCode:             String,
                                  email:                   String,
                                  phoneNumber:             String,
                                  communicationPreference: String,
                                  registrationChannel:     String,
                                  source:                  String)

  object PrePopulatedUserData {

    implicit val format: Format[PrePopulatedUserData] = Json.format[PrePopulatedUserData]
  }

  case class ManuallyEnteredDetails(accountName:   String,
                                    accountNumber: String,
                                    sortCode:      String,
                                    rollNumber:    String)

  object ManuallyEnteredDetails {

    implicit val format: Format[ManuallyEnteredDetails] = Json.format[ManuallyEnteredDetails]
  }
}

case class GetAccountResult(nino: String, account: JsValue)

object GetAccountResult {
  implicit val format: Format[GetAccountResult] = Json.format[GetAccountResult]
}

case class GetAccountResultEvent(getAccountResult: GetAccountResult, path: String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {
  val value: ExtendedDataEvent = {
    HTSEvent(appConfig.appName, "GetAccountResult", Json.toJson(getAccountResult), "get-account-result", path)
  }
}

case class BARSCheck(barsRequest: BankDetailsValidationRequest, response: JsValue, path: String)(implicit hc: HeaderCarrier, appConfig: AppConfig) extends HTSEvent {
  val value: ExtendedDataEvent =
    HTSEvent(appConfig.appName, "BARSCheck",
             Json.toJson(BARSCheck.Details(barsRequest.nino, barsRequest.accountNumber, barsRequest.sortCode, response)), "bars-check", path)

}

object BARSCheck {
  private case class Details(nino: String, accountNumber: String, sortCode: String, response: JsValue)

  private implicit val detailsFormat: Format[Details] = Json.format[Details]
}

