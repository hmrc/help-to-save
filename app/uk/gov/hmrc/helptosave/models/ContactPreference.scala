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

sealed trait ContactPreference

object ContactPreference {

  case object Email extends ContactPreference
  case object SMS extends ContactPreference

  implicit val contactPreferenceFormat: Format[ContactPreference] = new Format[ContactPreference] {
    def writes(o: ContactPreference): JsValue = o match {
      case ContactPreference.Email ⇒ JsString("email")
      case ContactPreference.SMS ⇒ JsString("sms")
    }
    def reads(o : JsValue) : JsResult[ContactPreference] = o match {
      case JsString(s) ⇒
        s.toLowerCase.trim match {
          case "email" ⇒ JsSuccess(ContactPreference.Email)
          case "sms" ⇒ JsSuccess(ContactPreference.SMS)
          case other ⇒ JsError(s"Could not read contact preference: $other")
        }

      case other ⇒ JsError(s"Expected string but got $other")
    }
  }

}
