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

package uk.gov.hmrc.helptosave.util.lock

import akka.actor.{Actor, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.helptosave.util.lock.LockProvider.TimePeriodLockProvider
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
 * This is an `Actor` which handles changing of state when a lock is acquired or a lock
 * is released. On start-up the `Lock` will try to acquire a lock using the given `LockProvider`.
 * If successful `onLockAcquired` is called on the `initialState`. Otherwise `onLockReleased` is
 * called on the `initialState`. The state is updated at this point.  When the lock duration defined
 * in the `LockProvider` has passed this `Actor` will try to acquire/renew a lock again. If successful
 * `onLockAcquired` is called on the current state. Otherwise `onLockReleased` is called on the
 * current state. This continues until the `Actor` dies.
 *
 * The actor will register a release of the lock using the `registerStopHook` function. For Play applications
 * the appropriate function is the `addStopHook` from the injectable `ApplicationLifecycle`. If this release
 * is successful `onLockReleased` is called.
 *
 * N.B.: If the process of trying to acquire/renew a lock fails for any reason the state is not changed.
 */
class Lock[State](lock:             LockProvider,
                  scheduler:        Scheduler,
                  initialState:     State,
                  onLockAcquired:   State ⇒ State,
                  onLockReleased:   State ⇒ State,
                  registerStopHook: (() ⇒ Future[Unit]) ⇒ Unit
) extends Actor with Logging {

  import Lock.LockMessages._
  import context.dispatcher

  var state: State = initialState

  var schedulerTask: Option[Cancellable] = None

  var lockAcquired: Boolean = false

  override def receive: Receive = {
    case AcquireLock ⇒
      val result = lock.tryToAcquireOrRenewLock[Unit](toFuture(()))
        .map(result ⇒ AcquireLockResult(result.isDefined))
        .recover{ case NonFatal(e) ⇒ AcquireLockFailure(e) }

      result pipeTo self

    case AcquireLockFailure(error) ⇒
      logger.warn(s"Could not acquire or renew lock: ${error.getMessage}. Leaving state as is")

    case AcquireLockResult(acquired) ⇒
      if (acquired) {
        logger.info(s"Lock successfully acquired (lockID: ${lock.lockId}")
        lockAcquired = true
        state = onLockAcquired(state)
      } else {
        logger.info(s"Unable to acquire lock (lockID: ${lock.lockId}")
        lockAcquired = false
        state = onLockReleased(state)
      }

  }

  override def preStart(): Unit = {
    super.preStart()

    // release the lock when the application shuts down
    registerStopHook{ () ⇒
      if (lockAcquired) {
        lock.releaseLock().onComplete {
          case Success(_) ⇒
            logger.info(s"Successfully released ${lock.lockId} lock")
            state = onLockReleased(state)
          case Failure(e) ⇒ logger.warn(s"Could not release ${lock.lockId} lock: ${e.getMessage}")
        }
      }
    }

    schedulerTask = Some(scheduler.scheduleAtFixedRate(Duration.Zero, lock.holdLockFor, self, AcquireLock))
  }

}

object Lock {

  /**
   * These props uses the `ExclusiveTimePeriodLock` to implement the
   * `LockProvider` behaviour required by the `Lock` actor
   */
  def props[State](mongoLockRepository: MongoLockRepository,
                   lockID:              String,
                   lockDuration:        FiniteDuration,
                   scheduler:           Scheduler,
                   initialState:        State,
                   onLockAcquired:      State ⇒ State,
                   onLockReleased:      State ⇒ State,
                   lifecycle:           ApplicationLifecycle): Props = {

    val lockProvider: TimePeriodLockProvider = TimePeriodLockProvider(
      repo        = mongoLockRepository,
      lockId      = lockID,
      holdLockFor = org.joda.time.Duration.millis(lockDuration.toMillis).getMillis.millis)

    Props(new Lock(lockProvider, scheduler, initialState, onLockAcquired, onLockReleased, lifecycle.addStopHook))
  }

  private sealed trait LockMessages

  private object LockMessages {
    case object AcquireLock extends LockMessages
    case class AcquireLockResult(lockAcquired: Boolean) extends LockMessages
    case class AcquireLockFailure(error: Throwable) extends LockMessages

  }

}

