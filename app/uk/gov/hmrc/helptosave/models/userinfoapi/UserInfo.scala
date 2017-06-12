/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models.userinfoapi

import java.time.LocalDate
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.models.userinfoapi.UserInfo.{Address, Enrolment}

/**
  * The user info in the format returned by the user info API
  */
case class UserInfo(given_name: Option[String],
                    family_name: Option[String],
                    middle_name: Option[String],
                    address: Option[Address],
                    birthdate: Option[LocalDate],
                    uk_gov_nino: Option[String],
                    hmrc_enrolments: Option[Seq[Enrolment]],
                    email: Option[String]){

  def isEmpty: Boolean =
    given_name.isEmpty &&
      family_name.isEmpty &&
      middle_name.isEmpty &&
      address.isEmpty &&
      birthdate.isEmpty &&
      uk_gov_nino.isEmpty &&
      hmrc_enrolments.isEmpty &&
      email.isEmpty
}


object UserInfo{

  case class Address(formatted: String,
                     postal_code: Option[String],
                     country: Option[String])

  case class EnrolmentIdentifier(key: String, value: String)

  case class Enrolment(key: String,
                       identifiers: Seq[EnrolmentIdentifier],
                       state: String)

  implicit val addressFormat: Format[Address] = Json.format[Address]
  implicit val enrolmentIdentifierFormat: Format[EnrolmentIdentifier] = Json.format[EnrolmentIdentifier]
  implicit val enrolmentFormat: Format[Enrolment] = Json.format[Enrolment]
  implicit val userInfoFormat: Format[UserInfo] = Json.format[UserInfo]

}
