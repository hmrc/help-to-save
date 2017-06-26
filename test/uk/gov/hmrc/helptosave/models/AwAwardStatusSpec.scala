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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsNumber, JsString, JsSuccess}

class AwAwardStatusSpec extends WordSpec with Matchers {

  "AwAwardStatus" must {

    "return a JsString value of O when AwAwardStatus Open is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Open)
      test shouldBe JsString("O")
    }

    "return a JsString value of F when AwAwardStatus Finalised is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Finalised)
      test shouldBe JsString("F")
    }

    "return a JsString value of T when AwAwardStatus Terminated is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Terminated)
      test shouldBe JsString("T")
    }

    "return a JsString value of P when AwAwardStatus Provisional is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Provisional)
      test shouldBe JsString("P")
    }

    "return a JsString value of C when AwAwardStatus Ceased is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Ceased)
      test shouldBe JsString("C")
    }

    "return a JsString value of Z when AwAwardStatus Deleted is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.writes(AwAwardStatus.Deleted)
      test shouldBe JsString("Z")
    }

    "return a JsResult value of Finalised when a JsValue of f is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("f"))
      test shouldBe JsSuccess(AwAwardStatus.Finalised)
    }

    "return a JsResult value of Terminated when a JsValue of t is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("t"))
      test shouldBe JsSuccess(AwAwardStatus.Terminated)
    }

    "return a JsResult value of Provisional when a JsValue of p is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("p"))
      test shouldBe JsSuccess(AwAwardStatus.Provisional)
    }

    "return a JsResult value of Ceased when a JsValue of c is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("c"))
      test shouldBe JsSuccess(AwAwardStatus.Ceased)
    }

    "return a JsResult value of Deleted when a JsValue of z is given" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("z"))
      test shouldBe JsSuccess(AwAwardStatus.Deleted)
    }

    "return a JsError with message 'Could not read aw_award_status" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsString("a"))
      test shouldBe JsError.apply("Could not read aw_award_status: a")
    }

    "return a JsError with message 'Expected string but got for aw_award_status 123" in {
      val test = AwAwardStatus.aw_award_statusFormat.reads(JsNumber(123))//scalastyle:ignore magic.number
      test shouldBe JsError.apply("Expected string but got for aw_award_status 123")
    }

  }
}
