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

package helpers

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.account.{Account, Blocking, BonusTerm}
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, YearMonth, ZoneId}

object TestData {

  val NINO = "JS329328A"

  def payloadJson(dobValue: String, communicationPreference: String = "02"): String =
    s"""{
            "nino" : "$NINO",
            "forename" : "name",
            "surname" : "surname",
            "dateOfBirth" : $dobValue,
            "contactDetails" : {
              "address1" : "1",
              "address2" : "2",
              "postcode": "postcode",
              "countryCode" : "country",
              "communicationPreference" : "$communicationPreference"
            },
            "nbaDetails": {
               "sortCode" : "20-12-12",
               "accountNumber" : "12345678",
               "rollNumber" : "11",
               "accountName" : "test"
             },
            "registrationChannel" : "online",
            "version" : "V2.0",
            "systemId" : "MDTP REGISTRATION"
      }""".stripMargin

  def createAccountJson(dobValue: String, detailsManuallyEntered: Boolean, communicationPreference: String = "02", source: String = "Digital"): String =
    s"""{
           "payload":${payloadJson(dobValue, communicationPreference)},
           "eligibilityReason":7,
           "source": "$source",
           "detailsManuallyEntered" : $detailsManuallyEntered
          }""".stripMargin

  def validCreateAccountRequestPayload(detailsManuallyEntered: Boolean = false,
                                       communicationPreference: String = "02",
                                       source: String = "Digital") =
    Json.parse(createAccountJson("20200101", detailsManuallyEntered, communicationPreference, source))

  val validCreateAccountRequest = validCreateAccountRequestPayload()
    .validate[CreateAccountRequest](CreateAccountRequest.createAccountRequestReads(Some("V2.0")))
    .getOrElse(sys.error("Could not parse CreateAccountRequest"))

  val validCreateAccountStrideRequest = validCreateAccountRequestPayload(source = "Stride-Manual")
    .validate[CreateAccountRequest](CreateAccountRequest.createAccountRequestReads(Some("V2.0")))
    .getOrElse(sys.error("Could not parse CreateAccountRequest"))

  val account = Account(
    YearMonth.of(2018, 1),
    "AC01", false,
    Blocking(false, false, false),
    200.34,
    34.50,
    15.50,
    50.00,
    LocalDate.parse("2018-02-28"),
    "Testforename",
    "Testsurname",
    Some("test@example.com"),
    List(
      BonusTerm(bonusEstimate = 123.45, bonusPaid = 123.45, startDate = LocalDate.parse("2018-01-01"), endDate = LocalDate.parse("2019-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2020-01-01")),
      BonusTerm(bonusEstimate = 67.00, bonusPaid = 0.00, startDate = LocalDate.parse("2020-01-01"), endDate = LocalDate.parse("2021-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2022-01-01"))
    ),
    None,
    None)
}
