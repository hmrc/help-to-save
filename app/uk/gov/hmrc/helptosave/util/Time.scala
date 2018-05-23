/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.util

import cats.syntax.eq._
import cats.instances.long._
import scala.annotation.tailrec

object Time {

  private val timeWordToDenomination = List(
    "ns" → 1000L,
    "μs" → 1000L,
    "ms" → 1000L,
    "s" → 60L,
    "m" → 60L,
    "h" → 24L,
    "d" → 7L
  )

  /** Return the integer part and the remainder of the result of dividing th enumerator by the denominator */
  private def divide(numerator: Long, denominator: Long): (Long, Long) =
    (numerator / denominator) → (numerator % denominator)

  /**
   * Convert `nanos` to a human-friendly string - will return the time in terms of
   * the two highest time resolutions that are appropriate. For example:
   *
   * 2 nanoseconds      -> "2ns"
   * 1.23456789 seconds -> "1s 234ms"
   */
  def nanosToPrettyString(nanos: Long): String = {

      @tailrec
      def loop(l:   List[(String, Long)],
               t:   Long,
               acc: List[(Long, String)]): List[(Long, String)] = l match {
        case Nil ⇒
          acc

        case (word, number) :: tail ⇒
          if (t < number) {
            (t → word) :: acc
          } else {
            val (remaining, currentUnits) = divide(t, number)

            if (currentUnits === 0L) {
              loop(tail, remaining, acc)
            } else {
              loop(tail, remaining, (currentUnits → word) :: acc)
            }
          }
      }

    val result = loop(timeWordToDenomination, nanos, List.empty[(Long, String)])
    result.take(2).map(x ⇒ s"${x._1}${x._2}").mkString(" ")
  }

}
