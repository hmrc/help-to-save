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

package uk.gov.hmrc.helptosave.util.lock

import uk.gov.hmrc.lock.ExclusiveTimePeriodLock

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// $COVERAGE-OFF$
trait LockProvider {

  val lockId: String

  val holdLockFor: FiniteDuration

  def releaseLock(): Future[Unit]

  def tryToAcquireOrRenewLock[T](body: ⇒ Future[T])(implicit ec: ExecutionContext): Future[Option[T]]

}

object LockProvider {

  /**
   * This lock provider ensures that some operation is only performed once across multiple
   * instances of an application. It is backed by [[ExclusiveTimePeriodLock]] from the
   * `mongo-lock` library
   */
  case class ExclusiveTimePeriodLockProvider(lock: ExclusiveTimePeriodLock) extends LockProvider {

    val lockId: String = lock.lockId

    val serverId: String = lock.serverId

    val holdLockFor: FiniteDuration = lock.holdLockFor.getMillis.millis

    override def releaseLock(): Future[Unit] =
      lock.repo.releaseLock(lockId, serverId)

    def tryToAcquireOrRenewLock[T](body: ⇒ Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      lock.tryToAcquireOrRenewLock(body)
  }

}
// $COVERAGE-ON$

