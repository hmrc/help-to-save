/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsSuccess, JsValue, Json}
import uk.gov.hmrc.helptosave.models.NSIPayload.ContactDetails

class NSIPayloadSpec extends WordSpec with Matchers {

  "NSIPayload" must {

    "have a reads instance" which {

      "correctly sets the version" in {
          def json(bankDetailsFieldName: String): String =
            s"""
             |{
             |  "forename" : "forename",
             |  "surname" : "surname",
             |  "dateOfBirth" : "19700101",
             |  "nino" : "nino",
             |  "contactDetails" : {
             |    "address1" : "address1",
             |    "address2" : "address2",
             |    "postcode": "postcode",
             |    "communicationPreference" : "preference"
             |  },
             |  "$bankDetailsFieldName": {
             |    "sortCode" : "123456",
             |    "accountNumber" : "12345678",
             |    "accountName" : "accountName"
             |  },
             |  "registrationChannel" : "channel",
             |  "systemId" : "systemId"
             |}""".stripMargin

        List[Option[String]](Some("version"), None).foreach{ version ⇒
          withClue(s"For version $version"){
            val expectedResult = JsSuccess(NSIPayload(
              "forename",
              "surname",
              LocalDate.of(1970, 1, 1),
              "nino",
              ContactDetails("address1", "address2", None, None, None, "postcode", None, None, None, "preference"),
              "channel",
              Some(BankDetails("123456", "12345678", None, "accountName")),
              version,
              Some("systemId")
            ))

            NSIPayload.nsiPayloadReads(version).reads(Json.parse(json("nbaDetails"))) shouldBe expectedResult
            NSIPayload.nsiPayloadReads(version).reads(Json.parse(json("bankDetails"))) shouldBe expectedResult
          }

        }

      }
    }

  }

}
