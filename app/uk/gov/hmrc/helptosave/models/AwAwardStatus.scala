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

sealed trait AwAwardStatus

object AwAwardStatus {

  case object Open extends AwAwardStatus

  case object Finalised extends AwAwardStatus

  case object Terminated extends AwAwardStatus

  case object Provisional extends AwAwardStatus

  case object Ceased extends AwAwardStatus

  case object Deleted extends AwAwardStatus

  implicit val aw_award_statusFormat: Format[AwAwardStatus] = new Format[AwAwardStatus] {
    def writes(o: AwAwardStatus): JsValue = o match {
      case AwAwardStatus.Open ⇒ JsString("O")
      case AwAwardStatus.Finalised ⇒ JsString("F")
      case AwAwardStatus.Terminated ⇒ JsString("T")
      case AwAwardStatus.Provisional ⇒ JsString("P")
      case AwAwardStatus.Ceased ⇒ JsString("C")
      case AwAwardStatus.Deleted ⇒ JsString("Z")
    }

    def reads(o: JsValue): JsResult[AwAwardStatus] = o match {
      case JsString(s) ⇒
        s.toLowerCase.trim match {
          case "o" ⇒ JsSuccess(AwAwardStatus.Open)
          case "f" ⇒ JsSuccess(AwAwardStatus.Finalised)
          case "t" ⇒ JsSuccess(AwAwardStatus.Terminated)
          case "p" ⇒ JsSuccess(AwAwardStatus.Provisional)
          case "c" ⇒ JsSuccess(AwAwardStatus.Ceased)
          case "z" ⇒ JsSuccess(AwAwardStatus.Deleted)
          case other ⇒ JsError(s"Could not read aw_award_status: $other")
        }

      case other ⇒ JsError(s"Expected string but got for aw_award_status $other")
    }
  }
}