/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models.enrolment

import play.api.libs.json.{Format, JsResult, JsValue, Json}

sealed trait Status

case class Enrolled(itmpHtSFlag: Boolean) extends Status

case object NotEnrolled extends Status

object Status {
  private case class EnrolmentStatusJSON(enrolled: Boolean, itmpHtSFlag: Boolean)

  private implicit val enrolmentStatusJSONFormat: Format[EnrolmentStatusJSON] = Json.format[EnrolmentStatusJSON]

  implicit val enrolmentStatusFormat: Format[Status] = new Format[Status] {
    override def writes(o: Status): JsValue = o match {
      case Enrolled(itmpHtSFlag) => Json.toJson(EnrolmentStatusJSON(enrolled = true, itmpHtSFlag = itmpHtSFlag))
      case NotEnrolled           => Json.toJson(EnrolmentStatusJSON(enrolled = false, itmpHtSFlag = false))
    }

    override def reads(json: JsValue): JsResult[Status] = json.validate[EnrolmentStatusJSON].map {
      case EnrolmentStatusJSON(true, flag) => Enrolled(flag)
      case EnrolmentStatusJSON(false, _)   => NotEnrolled
    }
  }
}
