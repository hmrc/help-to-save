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

import java.time.LocalDate

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.AccountCreated.{AllDetails, ExistingDetails, ManuallyEnteredDetails}
import uk.gov.hmrc.helptosave.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.audit.EventKeys.Path

class HtsEventSpec extends TestSupport {

  val appName = appConfig.appName

  "EligibilityCheckEvent" must { // scalastyle:off magic.number

    val nino = "AE123456C"
    val eligibleResult = EligibilityCheckResult("Eligible to HtS Account", 1, "In receipt of UC and income sufficient", 6)
    val inEligibleResult = EligibilityCheckResult("HtS account was previously created", 3, "HtS account already exists", 1)

    val eligibleUCClaimantWithinThreshold =
      s"""{"nino":"$nino","eligible":true,"isUCClaimant":true,"isWithinUCThreshold":true}""".stripMargin

    val eligibleUCClaimant =
      s"""{"nino":"$nino","eligible":true,"isUCClaimant":false}""".stripMargin

    val eligibleWithoutUCParams =
      s"""{"nino":"$nino","eligible":true}""".stripMargin

    val notEligibleWithUCParams =
      s"""{"nino":"$nino","eligible":false,"ineligibleReason":"Response: resultCode=3, reasonCode=1, meaning result='HtS account was previously created', reason='HtS account already exists'","isUCClaimant":true,"isWithinUCThreshold":true}""".stripMargin

    val notEligibleWithoutUCParams =
      s"""{"nino":"$nino","eligible":false,"ineligibleReason":"Response: resultCode=3, reasonCode=1, meaning result='HtS account was previously created', reason='HtS account already exists'"}""".stripMargin

    "be created with the appropriate auditSource and auditType" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None, "path")
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "eligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "read UC params if they are present when the user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = true, Some(true))), "path")
      event.value.detail.toString shouldBe eligibleUCClaimantWithinThreshold
      event.value.auditType shouldBe "eligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "not contain the UC params in the details when they are not passed and user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None, "path")
      event.value.detail.toString shouldBe eligibleWithoutUCParams
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "contain only the isUCClaimant param in the details but not isWithinUCThreshold" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = false, None)), "path")
      event.value.detail.toString shouldBe eligibleUCClaimant
      event.value.auditType shouldBe "eligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "read UC params if they are present when the user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, Some(UCResponse(ucClaimant = true, Some(true))), "path")
      event.value.detail.toString shouldBe notEligibleWithUCParams
      event.value.auditType shouldBe "eligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "not contain the UC params in the details when they are not passed and user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, None, "path")
      event.value.detail.toString shouldBe notEligibleWithoutUCParams
      event.value.auditType shouldBe "eligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }
  }

  "AccountCreated" must {

    "construct an event correctly" in {
      val nsiPayload = NSIPayload(
        "name",
        "surname",
        LocalDate.ofEpochDay(0),
        "nino",
        ContactDetails("line1", "line2", Some("line3"), None, None, "postcode", None, None, Some("email"), "comms"),
        "channel",
        Some(BankDetails("sortCode", "accountNumber", Some("rollNumber"), "accountName")),
        Some("version"),
        Some("id")
      )

      val event = AccountCreated(nsiPayload, "source")
      event.value.auditType shouldBe "accountCreated"
      event.value.tags.get(Path) shouldBe Some("/help-to-save/create-account")
      event.value.detail shouldBe Json.toJson(
        AllDetails(
          ExistingDetails(
            "name",
            "surname",
            "1970-01-01",
            "nino",
            "line1",
            "line2",
            "line3",
            "",
            "",
            "postcode",
            "",
            "email",
            "",
            "comms",
            "channel",
            "source"
          ), Some(ManuallyEnteredDetails(
            "accountName",
            "accountNumber",
            "sortCode",
            "rollNumber"
          ))
        )
      )

    }

  }

}
