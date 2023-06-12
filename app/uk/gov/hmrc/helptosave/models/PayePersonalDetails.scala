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
import java.time.format.DateTimeFormatter

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.Writes.temporalWrites
import play.api.libs.json._
import uk.gov.hmrc.helptosave.util.JsLookupHelper._
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
        name <- readName(json)
        dob <- readDob(json)
        address <- readAddress(json)
        phoneNumber <- readPhoneNumber(json)
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
      ) { x =>
          x.validate[Name].fold[JsResult[Name]](
            errors => JsError(s"could not read Name from PayePersonalDetails response, errors: $errors"),
            name => JsSuccess(name)
          )
        }
  }

  def readDob(json: JsValue): JsResult[LocalDate] = {
    read(json, "dateOfBirth")
      .fold[JsResult[LocalDate]](
        JsError("No DateOfBirth found in the DES response")
      ) { x =>
          x.validate[LocalDate].fold[JsResult[LocalDate]](
            errors => JsError(s"could not read DateOfBirth from PayePersonalDetails response, errors: $errors"),
            dob => JsSuccess(dob)
          )
        }
  }

  def readAddress(json: JsValue): JsResult[Address] = {
    readSeq(json, "addresses", "2").orElse(readSeq(json, "addresses", "1")) //1–Residential Address, 2–Correspondence Address
      .fold[JsResult[Address]](
        JsError("No Address found in the DES response")
      )(v =>
          for {
            line1 <- lookup("line1", v).validate[String]
            line2 <- lookup("line2", v).validate[String]
            line3 <- lookup("line3", v).validateOpt[String]
            line4 <- lookup("line4", v).validateOpt[String]
            line5 <- lookup("line5", v).validateOpt[String]
            postcode <- lookup("postcode", v).validate[String]
            countryCode <- lookup("countryCode", v).validateOpt[Int]
          } yield Address(line1, line2, line3, line4, line5, postcode, countryCode.flatMap(countryCodes.get).map(_.take(2)))
        )
  }

  def readPhoneNumber(json: JsValue): JsResult[Option[String]] = {
    readSeq(json, "phoneNumbers", "7").orElse(readSeq(json, "phoneNumbers", "1")) //7–Mobile Telephone Number, 1–Daytime Home Telephone Number
      .fold[JsResult[Option[String]]](
        JsSuccess(None)
      )(v =>
          for {
            callingCode <- lookup("callingCode", v).validateOpt[Int]
            convertedAreaDiallingCode <- lookup("convertedAreaDiallingCode", v).validateOpt[String]
            telephoneNumber <- lookup("telephoneNumber", v).validateOpt[String]
          } yield {
            (callingCode.flatMap(callingCodes.get), convertedAreaDiallingCode, telephoneNumber) match {
              case (Some(cc), Some(cadc), Some(t)) =>
                Some(s"+$cc${cadc.stripPrefix("0")}$t")
              case (None, Some(cadc), Some(t)) =>
                Some(s"$cadc$t")
              case (Some(cc), None, Some(t)) =>
                Some(s"+$cc${t.stripPrefix("0")}")
              case (None, None, Some(t)) =>
                Some(t)
              case _ =>
                None
            }
          })
  }
}
