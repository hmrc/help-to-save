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

case class PayePersonalDetails(name:        Name,
                               dateOfBirth: LocalDate,
                               address:     Address)

case class Name(firstForenameOrInitial: String,
                surname:                String
)

case class Address(line1:    String,
                   line2:    String,
                   line3:    Option[String],
                   line4:    Option[String],
                   line5:    Option[String],
                   postcode: String
)

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

        def readAddress(): JsResult[Address] =
          readData[Address](List(readSeq("addresses", "2"), readSeq("addresses", "1")), "Address") // 1–Residential Address, 2–Correspondence Address

      for {
        name ← readName()
        dob ← readDob()
        address ← readAddress()
      } yield {
        PayePersonalDetails(name, dob, address)
      }
    }

  }
}
