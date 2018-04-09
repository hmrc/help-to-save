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

        def readSeq(field: String, sequence: String): Option[JsValue] = (json \ field \ sequence).asOpt[JsValue]

        def read(field: String): Option[JsValue] = (json \ field).asOpt[JsValue]

        def readData[T](fields: List[Option[JsValue]], fieldLabel: String)(implicit tjs: Format[T]): JsResult[T] = {
          fields
            .find(_.isDefined)
            .fold[JsResult[T]](
              JsError(s"PayePersonalDetails : could not retrieve $fieldLabel for user")
            ) { x ⇒
                x.fold[JsResult[T]](
                  JsError(s"PayePersonalDetails : $fieldLabel not found for user")
                ) {
                    _.validate[T].fold[JsResult[T]](
                      errors ⇒ JsError(s"could not read $fieldLabel from PayePersonalDetails response, errors: $errors"),
                      name ⇒ JsSuccess(name)
                    )
                  }
              }
        }

        def readName(): JsResult[Name] =
          readData[Name](List(readSeq("names", "1"), readSeq("names", "2"), readSeq("names", "3")), "Name")

        def readDob(): JsResult[LocalDate] =
          readData[LocalDate](List(read("dateOfBirth")), "DateOfBirth")

        def readAddress(): JsResult[Address] = {
          List(readSeq("addresses", "2"), readSeq("addresses", "1")) //1–Residential Address, 2–Correspondence Address
            .find(_.isDefined)
            .fold[JsResult[Address]](
              JsError("PayePersonalDetails : could not retrieve Address for user")
            ) { x ⇒
                x.fold[JsResult[Address]](
                  JsError("PayePersonalDetails : could not retrieve Address for user")
                ) { v ⇒
                    val line1 = (v \ "line1").as[String]
                    val line2 = (v \ "line2").as[String]
                    val line3 = (v \ "line3").asOpt[String]
                    val line4 = (v \ "line4").asOpt[String]
                    val line5 = (v \ "line5").asOpt[String]
                    val postcode = (v \ "postcode").as[String]
                    val countryCode = (v \ "countryCode").as[Int]

                    JsSuccess(Address(line1, line2, line3, line4, line5, postcode, countryCodes.get(countryCode)))
                  }
              }
        }

        def readPhoneNumber(): JsResult[Option[String]] = {
          List(readSeq("phoneNumbers", "7"), readSeq("phoneNumbers", "1")) //7–Mobile Telephone Number, 1–Daytime Home Telephone Number
            .find(_.isDefined)
            .fold[JsResult[Option[String]]](
              JsSuccess(None)
            ) { x ⇒
                val mayBePhoneNumber = x.fold[Option[String]](
                  None
                ) { v ⇒
                  val callingCode = (v \ "callingCode").asOpt[Int]
                  val convertedAreaDiallingCode = (v \ "convertedAreaDiallingCode").asOpt[String]
                  val telephoneNumber = (v \ "telephoneNumber").asOpt[String]

                  (callingCode.flatMap(callingCodes.get), convertedAreaDiallingCode, telephoneNumber) match {
                    case (Some(cc), Some(cadc), Some(t)) ⇒
                      Some(s"+$cc${cadc.stripPrefix("0")}$t")
                    case (None, Some(cadc), Some(t)) ⇒
                      Some(s"$cadc$t")
                    case _ ⇒
                      None
                  }
                }
                JsSuccess(mayBePhoneNumber)
              }
        }

      for {
        name ← readName()
        dob ← readDob()
        address ← readAddress()
        phoneNumber ← readPhoneNumber()
      } yield {
        PayePersonalDetails(name, dob, address, phoneNumber)
      }
    }

  }
}
