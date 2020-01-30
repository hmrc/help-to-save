/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{Clock, LocalTime}

import scala.concurrent.duration._
trait TimeCalculator {

  def timeUntil(t: LocalTime): FiniteDuration

  def isNowInBetween(t1: LocalTime, t2: LocalTime): Boolean
}

class TimeCalculatorImpl(clock: Clock) extends TimeCalculator {

  private val twentyFourHoursInSeconds: Long = 24.hours.toSeconds

  def timeUntil(t: LocalTime): FiniteDuration = {
    val now = LocalTime.now(clock)

    val seconds = {
      val delta = now.until(t, java.time.temporal.ChronoUnit.SECONDS)
      if (delta < 0) {
        twentyFourHoursInSeconds + delta
      } else {
        delta
      }
    }

    seconds.seconds
  }

  def isNowInBetween(t1: LocalTime, t2: LocalTime): Boolean = {
    val now = LocalTime.now(clock)
    t1.isBefore(now) && t2.isAfter(now)
  }

}

