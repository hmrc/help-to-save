/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.metrics

import cats.instances.long._
import cats.syntax.eq._
import com.codahale.metrics.{Counter, Gauge, MetricRegistry, Timer}
import com.google.inject.{Inject, Singleton}

import scala.annotation.tailrec

@Singleton
class Metrics @Inject() (metrics: MetricRegistry) {

  protected def timer(name: String): Timer = metrics.timer(name)

  protected def counter(name: String): Counter = metrics.counter(name)

  protected def registerIntGauge(name: String, gauge: Gauge[Int]): Gauge[Int] = metrics.register(name, gauge)

  val itmpEligibilityCheckTimer: Timer = timer("backend.itmp-eligibility-check-time")

  val itmpEligibilityCheckErrorCounter: Counter = counter("backend.itmp-eligibility-check-error.count")

  val itmpSetFlagTimer: Timer = timer("backend.itmp-set-flag-time")

  val itmpSetFlagConflictCounter: Counter = counter("backend.itmp-set-flag-conflict.count")

  val itmpSetFlagErrorCounter: Counter = counter("backend.itmp-set-flag-error.count")

  val emailStoreUpdateTimer: Timer = timer("backend.email-store-update-time")

  val emailStoreUpdateErrorCounter: Counter = counter("backend.email-store-update-error.count")

  val emailStoreGetTimer: Timer = timer("backend.email-store-get-time")

  val emailStoreGetErrorCounter: Counter = counter("backend.email-store-get-error.count")

  val enrolmentStoreGetTimer: Timer = timer("backend.enrolment-store-get-time")

  val enrolmentStoreGetErrorCounter: Counter = counter("backend.enrolment-store-get-error.count")

  val enrolmentStoreUpdateTimer: Timer = timer("backend.enrolment-store-update-time")

  val enrolmentStoreUpdateErrorCounter: Counter = counter("backend.enrolment-store-update-error.count")

  val enrolmentStoreDeleteErrorCounter: Boolean => Counter = (revertSoftDelete: Boolean) =>
    if revertSoftDelete then {
      counter("backend.enrolment-store-undo-delete-error.count")
    } else {
      counter("backend.enrolment-store-delete-error.count")
    }

  val payePersonalDetailsTimer: Timer = timer("backend.paye-personal-details.time")

  val payePersonalDetailsErrorCounter: Counter = counter("backend.paye-personal-details-error.count")

  val getAccountTimer: Timer = timer("backend.get-account.time")

  val getAccountErrorCounter: Counter = counter("backend.get-account-error.count")

  val getTransactionsTimer: Timer = timer("backend.get-transactions.time")

  val getTransactionsErrorCounter: Counter = counter("backend.get-transactions-error.count")

  val eligibilityStatsTimer: Timer = timer("backend.eligibility-stats.time")

  val barsTimer: Timer = timer("backend.bars-timer")

  val barsErrorCounter: Counter = counter("backend.bars-error.count")

  def registerAccountStatsGauge(reason: String, channel: String, value: () => Int): Gauge[Int] =
    registerIntGauge(
      s"backend.create-account.$reason.$channel",
      new Gauge[Int] {
        override def getValue: Int = value()
      }
    )

}

object Metrics {

  private val timeWordToDenomination = List(
    "ns" -> 1000L,
    "μs" -> 1000L,
    "ms" -> 1000L,
    "s"  -> 60L,
    "m"  -> 60L,
    "h"  -> 24L,
    "d"  -> 7L
  )

  /** Return the integer part and the remainder of the result of dividing th enumerator by the denominator */
  private def divide(numerator: Long, denominator: Long): (Long, Long) =
    (numerator / denominator) -> (numerator % denominator)

  /** Convert `nanos` to a human-friendly string - will return the time in terms of the two highest time resolutions
    * that are appropriate. For example:
    *
    * 2 nanoseconds -> "2ns"
    * 1.23456789 seconds -> "1s 234ms"
    */
  def nanosToPrettyString(nanos: Long): String = {

    @tailrec
    def loop(l: List[(String, Long)], t: Long, acc: List[(Long, String)]): List[(Long, String)] = l match {
      case Nil =>
        acc

      case (word, number) :: tail =>
        if t < number then {
          (t -> word) :: acc
        } else {
          val (remaining, currentUnits) = divide(t, number)

          if currentUnits === 0L then {
            loop(tail, remaining, acc)
          } else {
            loop(tail, remaining, (currentUnits -> word) :: acc)
          }
        }
    }

    val result = loop(timeWordToDenomination, nanos, List.empty[(Long, String)])
    result.take(2).map(x => s"${x._1}${x._2}").mkString(" ")
  }

}
