/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.{Actor, ActorRef, Cancellable, Scheduler}
import uk.gov.hmrc.helptosave.util.WithExponentialBackoffRetry.ExponentialBackoffRetry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait WithExponentialBackoffRetry { this: Actor ⇒

  def exponentialBackoffRetry[RetryMessage, T](
      minBackoff:        FiniteDuration,
      maxBackoff:        FiniteDuration,
      exponentialFactor: Double,
      recipient:         ActorRef,
      message:           T ⇒ RetryMessage,
      scheduler:         Scheduler
  ): ExponentialBackoffRetry[RetryMessage, T] =
    ExponentialBackoffRetry(minBackoff, maxBackoff, exponentialFactor, recipient, message, scheduler)

}

object WithExponentialBackoffRetry {

  private[util] case class ExponentialBackoffRetry[RetryMessage, T](
      minBackoff:        FiniteDuration,
      maxBackoff:        FiniteDuration,
      exponentialFactor: Double,
      recipient:         ActorRef,
      message:           T ⇒ RetryMessage,
      scheduler:         Scheduler
  ) {
    private val minMillis: Double = minBackoff.toMillis
    private val maxMillis: Double = maxBackoff.toMillis

    private var numberOfRetries: Int = 0
    private var retryJob: Option[Cancellable] = None

    /**
     * Calculate the next retry time based on an exponential backoff strategy. The initial retry time is
     * [[minBackoff]]. The retry times exponentially increase to the value of [[maxBackoff]]. The rate at which the
     * [[maxBackoff]] value is reached is determined by [[exponentialFactor]].
     *
     * Let `m` be [[minBackoff]], and `M` be [[maxBackoff]] and `c` be [[exponentialFactor]]. Then the next retry time
     * is given by:
     * {{{
     *   t(n) = (m-M)e^(-cn) + M
     * }}}
     * where `n = 0,1,2,3,...`
     *
     */
    private def nextSendTime(): FiniteDuration = {
      val millis = (minMillis - maxMillis) * math.exp(-exponentialFactor * numberOfRetries.toDouble) + maxMillis
      millis.millis.min(maxBackoff)
    }

    def retry(b: T)(implicit ec: ExecutionContext): Option[FiniteDuration] = {
      if (!isActive) {
        val nextTime = nextSendTime()
        retryJob = Some(scheduler.scheduleOnce(nextTime){
          recipient ! message(b)
          retryJob = None
        })

        numberOfRetries += 1
        Some(nextTime)
      } else {
        None
      }
    }

    def cancelAndReset(): Unit = {
      retryJob.foreach(_.cancel())
      retryJob = None
      numberOfRetries = 0
    }

    def isActive: Boolean =
      retryJob.exists(!_.isCancelled)

  }

  private[util] object ExponentialBackoffRetry {

    /**
     * Creates an [[ExponentialBackoffRetry]] where the constant term `exponentialFactor` is calculated from
     * `numberOfRetriesUntilInitialWaitDoubles` in such a way that the retry time after `numberOfRetriesUntilInitialWaitDoubles`
     * retries is double the inital value
     */
    def apply[RetryMessage, T](
        minBackoff:                             FiniteDuration,
        maxBackoff:                             FiniteDuration,
        numberOfRetriesUntilInitialWaitDoubles: Int,
        recipient:                              ActorRef,
        message:                                T ⇒ RetryMessage,
        scheduler:                              Scheduler
    ): ExponentialBackoffRetry[RetryMessage, T] = {
      val minMillis = minBackoff.toMillis.toDouble
      val maxMillis = maxBackoff.toMillis.toDouble

      val c =
        math.log((minMillis - maxMillis) / (2.0 * minMillis - maxMillis)) / numberOfRetriesUntilInitialWaitDoubles.toDouble

      ExponentialBackoffRetry(
        minBackoff,
        maxBackoff,
        c,
        recipient,
        message,
        scheduler
      )
    }

  }

}

