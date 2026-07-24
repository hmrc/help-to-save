/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class HIPEligibilityCheckRequest(
  newTaxCreditStatus: String,
  workingTaxCreditEntitlement: String,
  workingTaxCreditTaperedHouseholdAward: BigDecimal,
  childTaxCreditTaperedHouseholdAward: BigDecimal,
  universalCreditAwardStatus: Option[Boolean],
  withinThreshold: Option[Boolean]
)

object HIPEligibilityCheckRequest {
  private val ninoNotFound               = "NINO not found"
  private val awardDoesNotIncludeWTC     = "Current Award does not include a WTC entitlement"
  private val taperedHouseholdAwardZero  = BigDecimal(0)
  private val childHouseholdAwardZero    = BigDecimal(0)

  def apply(ucResponse: Option[UCResponse]): HIPEligibilityCheckRequest = {
    val (universalCreditAwardStatus, withinThreshold) =
      ucResponse match {
        case Some(UCResponse(true, Some(withinThreshold))) => (Some(true), Some(withinThreshold))
        case Some(UCResponse(false, _))                    => (Some(false), None)
        case _                                             => (None, None)
      }

    HIPEligibilityCheckRequest(
      newTaxCreditStatus = ninoNotFound,
      workingTaxCreditEntitlement = awardDoesNotIncludeWTC,
      workingTaxCreditTaperedHouseholdAward = taperedHouseholdAwardZero,
      childTaxCreditTaperedHouseholdAward = childHouseholdAwardZero,
      universalCreditAwardStatus = universalCreditAwardStatus,
      withinThreshold = withinThreshold
    )
  }

  implicit val writes: OWrites[HIPEligibilityCheckRequest] = Json.writes[HIPEligibilityCheckRequest]
}

case class HIPEligibilityCheckResponse(eligibilityResult: String, eligibilityReason: String)

object HIPEligibilityCheckResponse {
  private val customerEligible     = "CUSTOMER ELIGIBLE FOR HTS ACCOUNT"
  private val customerIneligible   = "CUSTOMER INELIGIBLE FOR HTS ACCOUNT"
  private val accountHeldAlready   = "HTS ACCOUNT HELD ALREADY"
  private val manual               = "MANUAL"

  private val resultCodeAndTextByHipResult: Map[String, (Int, String)] = Map(
    customerEligible   -> (1, "Eligible to HtS Account"),
    customerIneligible -> (2, "Ineligible to HtS Account")
  )

  private val reasonCodeAndTextByHipReason: Map[String, (Int, String)] = Map(
    accountHeldAlready -> (1, "HtS account already exists"),
    "NOT ENTITLED TO WTC AND UC NOT CHECKED" ->
      (2, "Not entitled to WTC and UC not checked"),
    "ENTITLED TO WTC BUT NOT IN RECEIPT OF POSITIVE TAX CREDIT AND NOT IN RECEIPT DWP UC" ->
      (3, "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and not in receipt of UC"),
    "ENTITLED TO WTC BUT NOT IN RECEIPT OF POSITIVE TAX CREDIT AND IN RECEIPT DWP UC BUT INCOME INSUFFICIENT" ->
      (4, "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and in receipt of UC but income is insufficient"),
    "NOT ENTITLED TO WTC AND IN RECEIPT OF UC BUT INCOME INSUFFICIENT" ->
      (5, "Ineligible to HtS Account: Not entitled to WTC and in receipt of UC but income is insufficient"),
    "IN RECEIPT OF DWP UC AND INCOME SUFFICIENT" ->
      (6, "In receipt of UC and income sufficient"),
    "ENTITLED TO WTC AND RECEIVE POSITIVE TAX CREDIT" ->
      (7, "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit"),
    "ENTITLED TO WTC AND RECEIVE POSITIVE TAX CREDIT AND IN RECEIPT OF DWP UC AND INCOME SUFFICIENT" ->
      (8, "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient"),
    "NOT ENTITLED TO WTC AND NOT IN RECEIPT OF UC" ->
      (9, "Not entitled to WTC and not in receipt of UC")
  )

  implicit val reads: Reads[HIPEligibilityCheckResponse] = (
    ((__ \ "eligibilityResult").read[String] or (__ \ "result").read[String]) and
      ((__ \ "eligibilityReason").read[String] or (__ \ "reason").read[String])
  )(HIPEligibilityCheckResponse.apply)

  implicit val writes: OWrites[HIPEligibilityCheckResponse] = Json.writes[HIPEligibilityCheckResponse]

  extension (response: HIPEligibilityCheckResponse)
    def toEligibilityCheckResult: Either[String, EligibilityCheckResult] =
      response.eligibilityReason match {
        case `manual` =>
          Left("HIP eligibility returned MANUAL")

        case `accountHeldAlready` =>
          Right(EligibilityCheckResult("HtS account was previously created", 3, "HtS account already exists", 1))

        case hipReason =>
          for
            resultCodeAndText <- resultCodeAndTextByHipResult
                                   .get(response.eligibilityResult)
                                   .toRight(s"Unknown HIP eligibilityResult '${response.eligibilityResult}'")
            reasonCodeAndText <- reasonCodeAndTextByHipReason
                                   .get(hipReason)
                                   .toRight(s"Unknown HIP eligibilityReason '${response.eligibilityReason}'")
          yield EligibilityCheckResult(
            resultCodeAndText._2,
            resultCodeAndText._1,
            reasonCodeAndText._2,
            reasonCodeAndText._1
          )
      }
}
