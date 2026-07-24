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

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.util.UnitSpec

class HIPEligibilitySpec extends UnitSpec {

  "HIPEligibilityCheckRequest" must {
    "omit UC fields when UC details are unavailable" in {
      Json.toJson(HIPEligibilityCheckRequest(None)) shouldBe Json.obj(
        "newTaxCreditStatus" -> "NINO not found",
        "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
        "workingTaxCreditTaperedHouseholdAward" -> 0,
        "childTaxCreditTaperedHouseholdAward" -> 0
      )
    }

    "include both UC fields when UC claimant status is true" in {
      val request = HIPEligibilityCheckRequest(Some(UCResponse(ucClaimant = true, withinThreshold = Some(false))))

      Json.toJson(request) shouldBe
        Json.obj(
          "newTaxCreditStatus" -> "NINO not found",
          "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
          "workingTaxCreditTaperedHouseholdAward" -> 0,
          "childTaxCreditTaperedHouseholdAward" -> 0,
          "universalCreditAwardStatus" -> true,
          "withinThreshold" -> false
        )
    }

    "include only UC award status when UC claimant status is false" in {
      val request = HIPEligibilityCheckRequest(Some(UCResponse(ucClaimant = false, withinThreshold = None)))

      Json.toJson(request) shouldBe
        Json.obj(
          "newTaxCreditStatus" -> "NINO not found",
          "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
          "workingTaxCreditTaperedHouseholdAward" -> 0,
          "childTaxCreditTaperedHouseholdAward" -> 0,
          "universalCreditAwardStatus" -> false
        )
    }

    "avoid UC field combinations HIP rejects" in {
      val ucClaimantWithoutThreshold = HIPEligibilityCheckRequest(Some(UCResponse(ucClaimant = true, withinThreshold = None)))
      val nonUcClaimantWithThreshold = HIPEligibilityCheckRequest(Some(UCResponse(ucClaimant = false, withinThreshold = Some(true))))

      Json.toJson(ucClaimantWithoutThreshold) shouldBe
        Json.obj(
          "newTaxCreditStatus" -> "NINO not found",
          "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
          "workingTaxCreditTaperedHouseholdAward" -> 0,
          "childTaxCreditTaperedHouseholdAward" -> 0
        )

      Json.toJson(nonUcClaimantWithThreshold) shouldBe
        Json.obj(
          "newTaxCreditStatus" -> "NINO not found",
          "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
          "workingTaxCreditTaperedHouseholdAward" -> 0,
          "childTaxCreditTaperedHouseholdAward" -> 0,
          "universalCreditAwardStatus" -> false
        )
    }
  }

  "HIPEligibilityCheckResponse" must {
    "read the OpenAPI response field names" in {
      Json
        .parse(
          """
            |{
            |  "eligibilityResult": "CUSTOMER ELIGIBLE FOR HTS ACCOUNT",
            |  "eligibilityReason": "IN RECEIPT OF DWP UC AND INCOME SUFFICIENT"
            |}
            |""".stripMargin
        )
        .as[HIPEligibilityCheckResponse] shouldBe
        HIPEligibilityCheckResponse(
          "CUSTOMER ELIGIBLE FOR HTS ACCOUNT",
          "IN RECEIPT OF DWP UC AND INCOME SUFFICIENT"
        )
    }

    "read the current stub response field names" in {
      Json
        .parse(
          """
            |{
            |  "result": "CUSTOMER INELIGIBLE FOR HTS ACCOUNT",
            |  "reason": "NOT ENTITLED TO WTC AND NOT IN RECEIPT OF UC"
            |}
            |""".stripMargin
        )
        .as[HIPEligibilityCheckResponse] shouldBe
        HIPEligibilityCheckResponse(
          "CUSTOMER INELIGIBLE FOR HTS ACCOUNT",
          "NOT ENTITLED TO WTC AND NOT IN RECEIPT OF UC"
        )
    }

    "translate eligible HIP responses to the existing contract" in {
      HIPEligibilityCheckResponse(
        "CUSTOMER ELIGIBLE FOR HTS ACCOUNT",
        "ENTITLED TO WTC AND RECEIVE POSITIVE TAX CREDIT"
      ).toEligibilityCheckResult shouldBe Right(
        EligibilityCheckResult(
          "Eligible to HtS Account",
          1,
          "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit",
          7
        )
      )
    }

    "translate ineligible HIP responses to the existing contract" in {
      HIPEligibilityCheckResponse(
        "CUSTOMER INELIGIBLE FOR HTS ACCOUNT",
        "NOT ENTITLED TO WTC AND NOT IN RECEIPT OF UC"
      ).toEligibilityCheckResult shouldBe Right(
        EligibilityCheckResult(
          "Ineligible to HtS Account",
          2,
          "Not entitled to WTC and not in receipt of UC",
          9
        )
      )
    }

    "translate account-held HIP responses to the existing account-exists contract" in {
      HIPEligibilityCheckResponse(
        "CUSTOMER INELIGIBLE FOR HTS ACCOUNT",
        "HTS ACCOUNT HELD ALREADY"
      ).toEligibilityCheckResult shouldBe Right(
        EligibilityCheckResult(
          "HtS account was previously created",
          3,
          "HtS account already exists",
          1
        )
      )
    }

    "return an error for MANUAL" in {
      HIPEligibilityCheckResponse(
        "CUSTOMER INELIGIBLE FOR HTS ACCOUNT",
        "MANUAL"
      ).toEligibilityCheckResult shouldBe Left("HIP eligibility returned MANUAL")
    }
  }
}
