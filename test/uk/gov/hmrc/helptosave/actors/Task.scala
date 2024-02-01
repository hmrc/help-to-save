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

import scala.concurrent.duration.FiniteDuration

case class Task(delay: FiniteDuration, id: Long, runnable: Runnable, interval: Option[FiniteDuration])
    extends Ordered[Task] {

  def compare(t: Task): Int =
    if (delay > t.delay) -1
    else if (delay < t.delay) 1
    else if (id > t.id) -1
    else if (id < t.id) 1
    else 0
}
