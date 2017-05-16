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

package uk.gov.hmrc.helptosaveeligibilitycheck.models

import org.joda.time.LocalDate
import play.api.libs.json._

case class Award(aw_award_status: AwAwardStatus,
                 aw_tax_credit_period_startdate: LocalDate,
                 aw_tax_credit_period_end_date: LocalDate,
                 av_total_taper_household_award: Int,
                 ae_etc1_wtc_entitlement: Boolean,
                 av_end_date: LocalDate)

object Award {
  implicit val booleanFormat =   new Format[Boolean] {
    override def writes(o: Boolean): JsValue =  {
      if(o) JsString("Y") else JsString("N")
    }
    override def reads(json: JsValue): JsResult[Boolean] = json match {
      case JsString(s) ⇒
        s.toLowerCase.trim match {
          case "y" ⇒ JsSuccess(true)
          case "n" ⇒ JsSuccess(false)
          case other ⇒ JsError(s"Could not read ae_etc1_wtc_entitlement: $other")
        }
      case other ⇒ JsError(s"Expected string but got for ae_etc1_wtc_entitlement $other")
    }
  }

  implicit val awardFormat: Format[Award] = Json.format[Award]
}


