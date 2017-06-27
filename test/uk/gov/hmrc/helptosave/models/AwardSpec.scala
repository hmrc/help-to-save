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

import org.joda.time.LocalDate
import play.api.libs.json._
import uk.gov.hmrc.helptosave.utils.TestSupport


class AwardSpec extends TestSupport {

  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  "Award" must {

    "have a valid JSON format instance" in {
      val date = new LocalDate(2000, 1, 1) // scalastyle:ignore magic.number
      val awAwardStatus = AwAwardStatus
      val award = Award(AwAwardStatus.Open, date, date, 1, true, date)

      val jsValue = Json.toJson(award)
      Json.fromJson[Award](jsValue) shouldBe JsSuccess(award)
    }

    "have the booleanFormat writes method return JsString('N') when given a false value" in {
      val test = Award.booleanFormat.writes(false)
      test shouldBe JsString("N")
    }

    "have the booleanFormat reads method return false when given a JsValue of n" in {
      val test = Award.booleanFormat.reads(JsString("n"))
      test shouldBe JsSuccess(false)
    }

    "have the booleanFormat reads method return an error" in {
      val test = Award.booleanFormat.reads(JsString("something"))
      test shouldBe JsError("Could not read ae_etc1_wtc_entitlement: something")
    }

    "have the booleanFormat reads method return an expected string error" in {
      val test = Award.booleanFormat.reads(JsNumber(123))//scalastyle:ignore magic.number
      test shouldBe JsError("Expected string but got for ae_etc1_wtc_entitlement 123")
    }
  }
}
