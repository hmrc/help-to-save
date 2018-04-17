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
import java.time.format.DateTimeFormatter

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.Writes.temporalWrites
import play.api.libs.json._
import uk.gov.hmrc.helptosave.models.CallingCodes.callingCodes
import uk.gov.hmrc.helptosave.models.CountryCode.countryCodes

case class PayePersonalDetails(name:        Name,
                               dateOfBirth: LocalDate,
                               address:     Address,
                               phoneNumber: Option[String])

case class Name(firstForenameOrInitial: String, surname: String)

case class Address(line1:       String,
                   line2:       String,
                   line3:       Option[String],
                   line4:       Option[String],
                   line5:       Option[String],
                   postcode:    String,
                   countryCode: Option[String])

object PayePersonalDetails {

  implicit val nameFormat: Format[Name] = Json.format[Name]

  implicit val addressFormat: Format[Address] = Json.format[Address]

  implicit val dateFormat: Format[LocalDate] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    Format[LocalDate](localDateReads(formatter), temporalWrites[LocalDate, DateTimeFormatter](formatter))
  }

  implicit val format: Format[PayePersonalDetails] = new Format[PayePersonalDetails] {

    private val writesInstance = Json.writes[PayePersonalDetails]

    override def writes(o: PayePersonalDetails): JsValue = writesInstance.writes(o)

    override def reads(json: JsValue): JsResult[PayePersonalDetails] = {

      for {
        name ← readName(json)
        dob ← readDob(json)
        address ← readAddress(json)
        phoneNumber ← readPhoneNumber(json)
      } yield {
        PayePersonalDetails(name, dob, address, phoneNumber)
      }
    }
  }

  def readSeq(json: JsValue, field: String, sequence: String): Option[JsValue] = (json \ field \ sequence).asOpt[JsValue]

  def read(json: JsValue, field: String): Option[JsValue] = (json \ field).asOpt[JsValue]

  def readName(json: JsValue): JsResult[Name] = {
    readSeq(json, "names", "1").orElse(readSeq(json, "names", "2"))
      .fold[JsResult[Name]](
        JsError("No Name found in the DES response")
      ) { x ⇒
          x.validate[Name].fold[JsResult[Name]](
            errors ⇒ JsError(s"could not read Name from PayePersonalDetails response, errors: $errors"),
            name ⇒ JsSuccess(name)
          )
        }
  }

  def readDob(json: JsValue): JsResult[LocalDate] = {
    read(json, "dateOfBirth")
      .fold[JsResult[LocalDate]](
        JsError("No DateOfBirth found in the DES response")
      ) { x ⇒
          x.validate[LocalDate].fold[JsResult[LocalDate]](
            errors ⇒ JsError(s"could not read DateOfBirth from PayePersonalDetails response, errors: $errors"),
            dob ⇒ JsSuccess(dob)
          )
        }
  }

  def readAddress(json: JsValue): JsResult[Address] = {
    readSeq(json, "addresses", "2").orElse(readSeq(json, "addresses", "1")) //1–Residential Address, 2–Correspondence Address
      .fold[JsResult[Address]](
        JsError("No Address found in the DES response")
      )(v ⇒ {
          val line1 = (v \ "line1").as[String]
          val line2 = (v \ "line2").as[String]
          val line3 = (v \ "line3").asOpt[String]
          val line4 = (v \ "line4").asOpt[String]
          val line5 = (v \ "line5").asOpt[String]
          val postcode = (v \ "postcode").as[String]
          val countryCode = (v \ "countryCode").asOpt[Int]

          JsSuccess(Address(line1, line2, line3, line4, line5, postcode, countryCode.flatMap(countryCodes.get).map(_.take(2))))
        }
        )
  }

  def readPhoneNumber(json: JsValue): JsResult[Option[String]] = {
    readSeq(json, "phoneNumbers", "7").orElse(readSeq(json, "phoneNumbers", "1")) //7–Mobile Telephone Number, 1–Daytime Home Telephone Number
      .fold[JsResult[Option[String]]](
        JsSuccess(None)
      )(v ⇒ {
          val callingCode = (v \ "callingCode").asOpt[Int]
          val convertedAreaDiallingCode = (v \ "convertedAreaDiallingCode").asOpt[String]
          val telephoneNumber = (v \ "telephoneNumber").asOpt[String]

          JsSuccess {
            (callingCode.flatMap(callingCodes.get), convertedAreaDiallingCode, telephoneNumber) match {
              case (Some(cc), Some(cadc), Some(t)) ⇒
                Some(s"+$cc${cadc.stripPrefix("0")}$t")
              case (None, Some(cadc), Some(t)) ⇒
                Some(s"$cadc$t")
              case (Some(cc), None, Some(t)) ⇒
                Some(s"+$cc${t.stripPrefix("0")}")
              case (None, None, Some(t)) ⇒
                Some(t)
              case _ ⇒
                None
            }
          }
        })
  }
}
