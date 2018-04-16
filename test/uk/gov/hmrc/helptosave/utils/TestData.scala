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

package uk.gov.hmrc.helptosave.utils

import java.time.LocalDate

import uk.gov.hmrc.helptosave.models.{Address, Name, PayePersonalDetails}

trait TestData {

  def payeDetails(nino: String): String =
    s"""{
       |  "nino": "${nino.dropRight(1)}",
       |  "ninoSuffix": "${nino.takeRight(1)}",
       |  "names": {
       |    "1": {
       |      "sequenceNumber": 12345,
       |      "firstForenameOrInitial": "A",
       |      "surname": "Smith",
       |      "startDate": "2000-01-01"
       |    }
       |  },
       |  "sex": "M",
       |  "dateOfBirth": "1980-01-01",
       |  "deceased": false,
       |  "addresses": {
       |    "1": {
       |      "line1": "1 Station Road",
       |      "line2": "Town Centre",
       |      "line3": "Sometown",
       |      "line4": "Anyshire",
       |      "postcode": "AB12 3CD",
       |      "countryCode": 8,
       |      "line5": "UK",
       |      "sequenceNumber": 1,
       |      "startDate": "2000-01-01"
       |    }
       |  },
       | "phoneNumbers": {
       |    "1": {
       |	     "callingCode": 1,
       |       "telephoneType": 1,
       |	     "areaDiallingCode": "03000",
       |	     "telephoneNumber": "599614",
       |	     "convertedAreaDiallingCode": "020"
       |    }
       |  },
       |  "accountStatus": 0,
       |  "manualCorrespondenceInd": false,
       |  "dateOfEntry": "2000-01-01",
       |  "dateOfRegistration": "2000-01-01",
       |  "registrationType": 0,
       |  "hasSelfAssessmentAccount": false,
       |  "audioOutputRequired": false,
       |  "brailleOutputRequired": false,
       |  "largePrintOutputRequired": false,
       |  "welshOutputRequired": false
       |}""".stripMargin

  val ppDetails = PayePersonalDetails(
    Name("A", "Smith"),
    LocalDate.parse("1980-01-01"),
    Address("1 Station Road", "Town Centre", Some("Sometown"), Some("Anyshire"), Some("UK"), "AB12 3CD", Some("GB")), Some("+4420599614"))
}
