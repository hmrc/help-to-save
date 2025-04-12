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

import org.apache.pekko.actor.Cancellable

private case class MockCancellable(scheduler: MockScheduler, task: Task) extends Cancellable {

  private[this] var canceled: Boolean = false

  /** Possibly cancels this Cancellable. If the Cancellable has not already been canceled, or terminated after a single
    * execution, then the cancellable will be canceled. If cancel has already been called or the task has already
    * terminated, then no action will be taken.
    *
    * @return
    *   True if the Cancellable was canceled by THIS invocation of the cancel method, false otherwise.
    */
  override def cancel(): Boolean =
    this synchronized {
      if (canceled) {
        false
      } else {
        canceled = true
        scheduler.cancelTask(task)
        true
      }
    }

  /** True if this Cancellable has been canceled.
    *
    * @return
    *   Returns true if this cancellable has been canceled, false otherwise.
    */
  override def isCancelled: Boolean =
    this synchronized {
      canceled
    }

}
