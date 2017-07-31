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

package uk.gov.hmrc.helptosave.models

import play.api.libs.json._

trait MissingUserInfo

object MissingUserInfo {

  case object GivenName extends MissingUserInfo

  case object Surname extends MissingUserInfo

  case object Email extends MissingUserInfo

  case object DateOfBirth extends MissingUserInfo

  case object Contact extends MissingUserInfo

  implicit val missingInfoFormat: Format[MissingUserInfo] = new Format[MissingUserInfo] {
    override def reads(json: JsValue): JsResult[MissingUserInfo] = {
      json match {
        case JsString("GivenName") ⇒ JsSuccess(GivenName)
        case JsString("Surname") ⇒ JsSuccess(Surname)
        case JsString("Email") ⇒ JsSuccess(Email)
        case JsString("DateOfBirth") ⇒ JsSuccess(DateOfBirth)
        case JsString("Contact") => JsSuccess(Contact)
        case _ => JsError("unknown field for MissingUserInfo")
      }
    }

    override def writes(missingType: MissingUserInfo): JsValue = {
      val result = missingType match {
        case GivenName ⇒ "GivenName"
        case Surname ⇒ "Surname"
        case Email => "Email"
        case DateOfBirth => "DateOfBirth"
        case Contact ⇒ "Contact"
      }
      JsString(result)
    }
  }
}

