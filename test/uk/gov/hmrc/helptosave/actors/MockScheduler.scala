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

import org.apache.pekko.actor.{Cancellable, Scheduler}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** A scheduler whose `tick` can be triggered manually, which is helpful for testing purposes. Tasks are executed
  * synchronously when `tick` is called.
  *
  * Typically this scheduler is used indirectly via a [[VirtualTime]] instance.
  */
class MockScheduler(time: VirtualTime) extends Scheduler {

  private[this] var id = 0L

  // Tasks are sorted descendingly by execution priority, i.e. head is the largest element and thus executed next.
  private[this] var tasks = new collection.mutable.PriorityQueue[Task]()

  /** Runs any tasks that are due at this point in time. This includes running recurring tasks multiple times if needed.
    * The execution of tasks happens synchronously in the calling thread.
    *
    * Tasks are executed in order based on their scheduled execution time. We do not define the execution ordering of
    * tasks that are scheduled for the same time.
    *
    * Implementation detail: If you are using this scheduler indirectly via a [[VirtualTime]] instance, then this method
    * will be called automatically by the [[VirtualTime]] instance, and you should not manually call it.
    */
  def tick(): Unit =
    time.lock synchronized {
      while (tasks.nonEmpty && tasks.head.delay <= time.elapsed) {
        val head = tasks.dequeue()
        head.runnable.run()
        head.interval match {
          case Some(interval) => tasks += Task(head.delay + interval, head.id, head.runnable, head.interval)
          case None           =>
        }
      }
    }

  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit
    executor: ExecutionContext
  ): Cancellable =
    addToTasks(delay, runnable, None)

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(implicit
    executor: ExecutionContext
  ): Cancellable =
    addToTasks(initialDelay, runnable, Option(interval))

  private def addToTasks(delay: FiniteDuration, runnable: Runnable, interval: Option[FiniteDuration]): Cancellable =
    time.lock synchronized {
      id += 1
      val startTime = time.elapsed + delay
      val task      = Task(startTime, id, runnable, interval)
      tasks += task
      MockCancellable(this, task)
    }

  def cancelTask(task: Task): Unit =
    time.lock synchronized {
      tasks = tasks.filterNot { x =>
        x.id == task.id
      }
    }

  /** The maximum frequency is 1000 Hz.
    */
  override val maxFrequency: Double = 1.second / 1.millis

}

class VirtualTime {

  /** There's a circular dependency between the states of [[MockScheduler]] and this class, hence we use the same lock
    * for both.
    */
  val lock = new Object

  private[this] var elapsedTime: FiniteDuration = 0.millis

  val scheduler = new MockScheduler(this)

  private lazy val minimumAdvanceStep = 1.second / scheduler.maxFrequency

  /** Returns how much "time" has elapsed so far.
    *
    * @return
    *   elapsed time
    */
  def elapsed: FiniteDuration = lock synchronized {
    elapsedTime
  }

  /** Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
    *
    * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has
    * completed.
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

  /** Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
    *
    * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has
    * completed.
    *
    * @param millis
    *   step in milliseconds
    */
  def advance(millis: Long): Unit = advance(FiniteDuration(millis, TimeUnit.MILLISECONDS))

  override def toString: String = s"${getClass.getSimpleName}(${elapsed.toMillis})"

}
