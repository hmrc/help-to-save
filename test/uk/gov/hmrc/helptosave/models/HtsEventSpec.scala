/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.helptosave.models.AccountCreated.{AllDetails, ManuallyEnteredDetails, PrePopulatedUserData}
import uk.gov.hmrc.helptosave.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.audit.EventKeys._

class HtsEventSpec extends TestSupport {

  val appName = appConfig.appName

  "EligibilityCheckEvent" must { // scalastyle:off magic.number

    val nino = "AE123456C"
    val eligibleResult =
      EligibilityCheckResult("Eligible to HtS Account", 1, "In receipt of UC and income sufficient", 6)
    val inEligibleResult =
      EligibilityCheckResult("HtS account was previously created", 3, "HtS account already exists", 1)

    val eligibleUCClaimantWithinThreshold =
      s"""{"nino":"$nino","eligible":true,"isUCClaimant":true,"isWithinUCThreshold":true}""".stripMargin

    val eligibleUCClaimant =
      s"""{"nino":"$nino","eligible":true,"isUCClaimant":false}""".stripMargin

    val eligibleWithoutUCParams =
      s"""{"nino":"$nino","eligible":true}""".stripMargin

    val notEligibleWithUCParams =
      s"""{"nino":"$nino","eligible":false,"ineligibleReason": {"resultCode": 3, "reasonCode" : 1, "result": "HtS account was previously created", "reason": "HtS account already exists"},"isUCClaimant":true,"isWithinUCThreshold":true}""".stripMargin

    val notEligibleWithoutUCParams =
      s"""{"nino":"$nino","eligible":false,"ineligibleReason": {"resultCode": 3, "reasonCode" : 1, "result": "HtS account was previously created", "reason": "HtS account already exists"}}""".stripMargin

    "be created with the appropriate auditSource and auditType" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None, "path")
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "read UC params if they are present when the user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = true, Some(true))), "path")
      event.value.detail.toString shouldBe eligibleUCClaimantWithinThreshold
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "not contain the UC params in the details when they are not passed and user is eligible" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, None, "path")
      event.value.detail.toString shouldBe eligibleWithoutUCParams
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "contain only the isUCClaimant param in the details but not isWithinUCThreshold" in {
      val event = EligibilityCheckEvent(nino, eligibleResult, Some(UCResponse(ucClaimant = false, None)), "path")
      event.value.detail.toString shouldBe eligibleUCClaimant
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "read UC params if they are present when the user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, Some(UCResponse(ucClaimant = true, Some(true))), "path")
      event.value.detail shouldBe Json.parse(notEligibleWithUCParams)
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }

    "not contain the UC params in the details when they are not passed and user is NOT eligible" in {
      val event = EligibilityCheckEvent(nino, inEligibleResult, None, "path")
      event.value.detail shouldBe Json.parse(notEligibleWithoutUCParams)
      event.value.auditType shouldBe "EligibilityResult"
      event.value.tags.get(Path) shouldBe Some("path")
    }
  }

  "AccountCreated" must {

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

    "construct an event correctly when the details have not been manually entered" in {

      val event = AccountCreated(nsiPayload, "source", false)
      event.value.auditType shouldBe "AccountCreated"
      event.value.tags.get(Path) shouldBe Some("/help-to-save/create-account")
      event.value.detail shouldBe Json.toJson(
        AllDetails(
          PrePopulatedUserData(
            Some("name"),
            Some("surname"),
            Some("1970-01-01"),
            Some("line1"),
            Some("line2"),
            Some("line3"),
            None,
            None,
            Some("postcode"),
            None,
            Some("email"),
            None,
            "nino",
            "comms",
            "channel",
            "source"
          ),
          ManuallyEnteredDetails(
            "accountName",
            "accountNumber",
            "sortCode",
            Some("rollNumber")
          )
        )
      )

    }

    "construct an event correctly when the details have been manually entered" in {

      val event = AccountCreated(nsiPayload, "source", true)
      event.value.auditType shouldBe "AccountCreated"
      event.value.tags.get(Path) shouldBe Some("/help-to-save/create-account")
      event.value.detail shouldBe Json.toJson(
        AllDetails(
          PrePopulatedUserData(
            "nino",
            "comms",
            "channel",
            "source"
          ),
          ManuallyEnteredDetails(
            Some("accountName"),
            Some("accountNumber"),
            Some("sortCode"),
            Some("rollNumber"),
            Some("name"),
            Some("surname"),
            Some("1970-01-01"),
            Some("line1"),
            Some("line2"),
            Some("line3"),
            None,
            None,
            Some("postcode"),
            None,
            Some("email"),
            None
          )
        )
      )

    }

  }

  "GetAccountResultEvent" must {

    val nino = randomNINO()

    val nsiAccountJson = Json.parse("""
                                      |{
                                      |  "accountNumber": "AC01",
                                      |  "accountBalance": "200.34",
                                      |  "accountClosedFlag": "",
                                      |  "accountBlockingCode": "00",
                                      |  "clientBlockingCode": "00",
                                      |  "currentInvestmentMonth": {
                                      |    "investmentRemaining": "15.50",
                                      |    "investmentLimit": "50.00",
                                      |    "endDate": "2018-02-28"
                                      |  },
                                      |  "clientForename":"Testforename",
                                      |  "clientSurname":"Testsurname",
                                      |  "emailAddress":"test@example.com",
                                      |  "terms": [
                                      |     {
                                      |       "termNumber":2,
                                      |       "startDate":"2020-01-01",
                                      |       "endDate":"2021-12-31",
                                      |       "bonusEstimate":"67.00",
                                      |       "bonusPaid":"0.00"
                                      |    },
                                      |    {
                                      |       "termNumber":1,
                                      |       "startDate":"2018-01-01",
                                      |       "endDate":"2019-12-31",
                                      |       "bonusEstimate":"123.45",
                                      |       "bonusPaid":"123.45"
                                      |    }
                                      |  ]
                                      |}
      """.stripMargin).as[JsObject]

    "construct GetAccountResult event correctly" in {

      val getAccountResult = GetAccountResult(nino, nsiAccountJson)
      val event = GetAccountResultEvent(getAccountResult, "path")

      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "GetAccountResult"
      event.value.tags.get(TransactionName) shouldBe Some("get-account-result")
      event.value.tags.get(Path) shouldBe Some("path")
      event.value.detail shouldBe Json.toJson(getAccountResult)
    }
  }

  "BARSCheck" must {

    "be created correctly" in {
      val response = Json.parse("""{ "a" : "b" }""")
      val event = BARSCheck(BankDetailsValidationRequest("nino", "code", "number"), response, "path")
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "BARSCheck"
      event.value.detail shouldBe Json.parse(
        s"""{
           | "nino": "nino",
           | "accountNumber" : "number",
           | "sortCode": "code",
           | "response" : ${response.toString}
           | }
        """.stripMargin
      )
      event.value.tags.get("path") shouldBe Some("path")
    }

  }
}
