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

import uk.gov.hmrc.helptosave.utils.TestSupport

class HtsEventSpec extends TestSupport {

  val appName = appConfig.appName

  "EligibilityCheckEvent" must { // scalastyle:off magic.number

    val nino = "AE123456C"
    val eligibleResult = EligibilityCheckResult("Eligible to HtS Account", 1, "In receipt of UC and income sufficient", 6)
    val inEligibleResult = EligibilityCheckResult("HtS account was previously created", 3, "HtS account already exists", 1)

    "be created with the appropriate auditSource and auditType" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "EligibilityResult"
    }

    "read UC params if they are present when the user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = true, Some(true))))
      event.value.detail.size shouldBe 4
      event.value.detail.exists(x ⇒ x._1 === "eligible" && x._2 === "true") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isUCClaimant" && x._2 === "true") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isWithinUCThreshold" && x._2 === "true") shouldBe true
    }

    "not contain the UC params in the details when they are not passed and user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None)
      event.value.detail.size shouldBe 2
      event.value.detail.exists(x ⇒ x._1 === "eligible" && x._2 === "true") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isUCClaimant" && x._2 === "true") shouldBe false
      event.value.detail.exists(x ⇒ x._1 === "isWithinUCThreshold" && x._2 === "true") shouldBe false
    }

    "contain only the isUCClaimant param in the details but not isWithinUCThreshold" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = false, None)))
      event.value.detail.size shouldBe 3
      event.value.detail.exists(x ⇒ x._1 === "eligible" && x._2 === "true") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isUCClaimant" && x._2 === "false") shouldBe true
    }

    "read UC params if they are present when the user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, Some(UCResponse(ucClaimant = true, Some(true))))
      event.value.detail.size shouldBe 5
      event.value.detail.exists(x ⇒ x._1 === "eligible" && x._2 === "false") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isUCClaimant" && x._2 === "true") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isWithinUCThreshold" && x._2 === "true") shouldBe true
    }

    "not contain the UC params in the details when they are not passed and user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, None)
      event.value.detail.size shouldBe 3
      event.value.detail.exists(x ⇒ x._1 === "eligible" && x._2 === "false") shouldBe true
      event.value.detail.exists(x ⇒ x._1 === "isUCClaimant" && x._2 === "true") shouldBe false
      event.value.detail.exists(x ⇒ x._1 === "isWithinUCThreshold" && x._2 === "true") shouldBe false
    }

  }

}
