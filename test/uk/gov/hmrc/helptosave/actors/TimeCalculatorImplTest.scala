/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.actors

import java.time._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration._

// scalastyle:off magic.number
class TimeCalculatorImplTest extends AnyWordSpec with Matchers {

  "TimeCalculatorImpl" must {

    // create a clock fixed a 1am
    val clock =
      Clock.fixed(
        LocalTime.parse("01:00")
          .atDate(LocalDate.ofEpochDay(0L))
          .toInstant(ZoneOffset.UTC),
        ZoneId.of("Z")
      )

    val calculator = new TimeCalculatorImpl(clock)

    "be able to tell if now is between two times" in {
      calculator.isNowInBetween(LocalTime.MIDNIGHT, LocalTime.of(2, 0)) shouldBe true
      calculator.isNowInBetween(LocalTime.of(2, 0), LocalTime.of(3, 0)) shouldBe false
    }

    "calculate time between two times correctly" in {

      val t1 = LocalTime.of(13, 24, 56)
      val timeUntilT1 = 12.hours + 24.minutes + 56.seconds
      calculator.timeUntil(t1) shouldBe timeUntilT1

      val t2 = LocalTime.of(0, 11, 22)
      val timeUntilT2 = 23.hours + 11.minutes + 22.seconds
      calculator.timeUntil(t2) shouldBe timeUntilT2
    }

  }

}
// scalastyle:on magic.number
