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

import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosave.models.PayePersonalDetails.*
import uk.gov.hmrc.helptosave.utils.{TestData, TestSupport}

import java.time.LocalDate

class PayePersonalDetailsSpec extends TestSupport with TestData { // scalastyle:off magic.number

  val nino = "AE123456C"

  "The PayePersonalDetails" when {

    "parsing Names" must {
      def nameJson(type1: Int, type2: Int) =
        s"""{
           |  "names": {
           |    "$type1": {
           |      "sequenceNumber": 12345,
           |      "firstForenameOrInitial": "AAA",
           |      "surname": "BBB",
           |      "startDate": "2018-01-01"
           |    },
           |   "$type2": {
           |      "sequenceNumber": 678910,
           |      "firstForenameOrInitial": "CCC",
           |      "surname": "DDD",
           |      "startDate": "2018-01-01"
           |    }
           |  }
          }""".stripMargin

      "read Real name first" in {
        readName(Json.parse(nameJson(1, 2))) shouldBe JsSuccess(Name("AAA", "BBB"))
      }

      "read Known-As name if Real name does not exist" in {
        readName(Json.parse(nameJson(8, 2))) shouldBe JsSuccess(Name("CCC", "DDD"))
      }
      "return error if no Real and Known-As names not found" in {
        readName(Json.parse(nameJson(8, 6))) shouldBe JsError("No Name found in the DES response")
      }
    }

    "parsing Addresses" must {

      def addressJson(type1: Int, type2: Int) =
        s"""{
           |  "addresses": {
           |    "$type1": {
           |      "line1": "Residential line1",
           |      "line2": "Residential line2",
           |      "line3": "Residential line3",
           |      "line4": "Residential line4",
           |      "postcode": "Residential Postcode",
           |      "countryCode": 1,
           |      "line5": "Residential line5",
           |      "sequenceNumber": 1,
           |      "startDate": "2000-01-01"
           |    },
           |    "$type2": {
           |      "line1": "Correspondence line1",
           |      "line2": "Correspondence line2",
           |      "line3": "Correspondence line3",
           |      "line4": "Correspondence line4",
           |      "postcode": "Correspondence Postcode",
           |      "countryCode": 1,
           |      "line5": "Correspondence line5",
           |      "sequenceNumber": 1,
           |      "startDate": "2000-01-01"
           |    }
           |  }
           |  }
         """.stripMargin

      "read the Correspondence Address first" in {
        readAddress(Json.parse(addressJson(1, 2))) shouldBe JsSuccess(
          Address(
            "Correspondence line1",
            "Correspondence line2",
            Some("Correspondence line3"),
            Some("Correspondence line4"),
            Some("Correspondence line5"),
            "Correspondence Postcode",
            Some("GB")
          )
        )
      }

      "read the Residential Address if Correspondence address is not found" in {
        readAddress(Json.parse(addressJson(1, 5))) shouldBe JsSuccess(
          Address(
            "Residential line1",
            "Residential line2",
            Some("Residential line3"),
            Some("Residential line4"),
            Some("Residential line5"),
            "Residential Postcode",
            Some("GB")
          )
        )
      }

      "return error if no Correspondence and Residential names not found" in {
        readAddress(Json.parse(addressJson(10, 15))) shouldBe JsError("No Address found in the DES response")
      }

      "handle gracefully in case countryCode is not found in the DES response" in {
        val json =
          """{
            |  "addresses": {
            |    "1": {
            |      "line1": "Residential line1",
            |      "line2": "Residential line2",
            |      "line3": "Residential line3",
            |      "line4": "Residential line4",
            |      "postcode": "Residential Postcode",
            |      "line5": "Residential line5",
            |      "sequenceNumber": 1,
            |      "startDate": "2000-01-01"
            |    }
            |  }
            |  }
         """.stripMargin

        readAddress(Json.parse(json)) shouldBe JsSuccess(
          Address(
            "Residential line1",
            "Residential line2",
            Some("Residential line3"),
            Some("Residential line4"),
            Some("Residential line5"),
            "Residential Postcode",
            None
          )
        )
      }

    }

    "parsing DateOfBirth" must {

      val dobJson =
        """{
          "dateOfBirth": "1980-01-01"
          }""".stripMargin

      "return date of birth value as expected" in {
        readDob(Json.parse(dobJson)) shouldBe JsSuccess(LocalDate.of(1980, 1, 1))
      }

      "throw error if Date of Birth is not found in the json" in {
        readDob(Json.parse("""{"foo": "bar"}""")) shouldBe JsError("No DateOfBirth found in the DES response")
      }
    }

    "parsing phone numbers" must {

      def phoneJson(type1: Int, type2: Int, callingCode: Option[Int] = None) =
        s"""{"phoneNumbers": {
           |    "$type1": {
           |	     "callingCode": ${callingCode.getOrElse(1)},
           |       "telephoneType": $type1,
           |	     "areaDiallingCode": "03000",
           |	     "telephoneNumber": "599614",
           |	     "convertedAreaDiallingCode": "020"
           |      },
           |    "$type2": {
           |	     "callingCode": ${callingCode.getOrElse(1)},
           |       "telephoneType": $type2,
           |	     "telephoneNumber": "07841096720"
           |     }
           |    }
              }""".stripMargin

      "read only mobile type if multiple phone types exist" in {
        readPhoneNumber(Json.parse(phoneJson(1, 7))) shouldBe JsSuccess(Some("+447841096720"))
      }

      "read Day time Telephone type next if mobile type does NOT exist" in {
        readPhoneNumber(Json.parse(phoneJson(1, 2))) shouldBe JsSuccess(Some("+4420599614"))
      }

      "return None if both mobileNumber and Day time Telephone types do NOT exist" in {
        readPhoneNumber(Json.parse(phoneJson(2, 3))) shouldBe JsSuccess(None)
      }

      "handle the case when callingCode does NOT exist" in {

        val json =
          """{"phoneNumbers": {
            |    "1": {
            |       "telephoneType": 1,
            |	     "areaDiallingCode": "03000",
            |	     "telephoneNumber": "599614",
            |	     "convertedAreaDiallingCode": "020"
            |      }
            |  }
             }""".stripMargin

        readPhoneNumber(Json.parse(json)) shouldBe JsSuccess(Some("020599614"))
      }

      "handle the case when convertedAreaDiallingCode does NOT exist" in {

        val json =
          """{"phoneNumbers": {
            |    "1": {
            |       "callingCode": 1,
            |       "telephoneType": 1,
            |	     "areaDiallingCode": "03000",
            |	     "telephoneNumber": "059961478"
            |      }
            |  }
             }""".stripMargin

        readPhoneNumber(Json.parse(json)) shouldBe JsSuccess(Some("+4459961478"))
      }

      "returns None when telephoneNumber does NOT exist" in {

        val json =
          """{"phoneNumbers": {
            |    "1": {
            |       "callingCode": 1,
            |       "telephoneType": 1,
            |	     "areaDiallingCode": "03000",
            |	     "convertedAreaDiallingCode": "020"
            |      }
            |  }
             }""".stripMargin

        readPhoneNumber(Json.parse(json)) shouldBe JsSuccess(None)
      }

      "returns only telephoneNumber when callingCode amd convertedAreaDiallingCode do NOT exist" in {

        val json =
          """{"phoneNumbers": {
            |    "1": {
            |       "telephoneType": 1,
            |	     "areaDiallingCode": "03000",
            |	     "telephoneNumber": "59961478"
            |      }
            |  }
             }""".stripMargin

        readPhoneNumber(Json.parse(json)) shouldBe JsSuccess(Some("59961478"))
      }
    }
  }

}
