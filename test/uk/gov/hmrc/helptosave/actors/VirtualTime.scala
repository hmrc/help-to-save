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

package uk.gov.hmrc.helptosave.actors

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class VirtualTime {

  /**
   * There's a circular dependency between the states of [[MockScheduler]] and this class,
   * hence we use the same lock for both.
   */
  val lock = new Object

  private[this] var elapsedTime: FiniteDuration = 0.millis

  val scheduler = new MockScheduler(this)

  private lazy val minimumAdvanceStep = 1.second / scheduler.maxFrequency

  /**
   * Returns how much "time" has elapsed so far.
   *
   * @return elapsed time
   */
  def elapsed: FiniteDuration = lock synchronized {
    elapsedTime
  }

  /**
   * Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
   *
   * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has completed.
   *
   * @param step
   */
  def advance(step: FiniteDuration): Unit = {
    require(step >= minimumAdvanceStep, s"minimum supported step is $minimumAdvanceStep")
    lock synchronized {
      elapsedTime += step
      scheduler.tick()
    }
  }

  /**
   * Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
   *
   * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has completed.
   *
   * @param millis step in milliseconds
   */
  def advance(millis: Long): Unit = advance(FiniteDuration(millis, TimeUnit.MILLISECONDS))

  override def toString: String = s"${getClass.getSimpleName}(${elapsed.toMillis})"

}
