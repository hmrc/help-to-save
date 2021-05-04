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

package uk.gov.hmrc.helptosave.util.lock

import akka.actor.{ActorRef, Props}
import com.miguno.akka.testing.VirtualTime
import uk.gov.hmrc.helptosave.actors.ActorTestSupport

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

class LockSpec extends ActorTestSupport("LockSpec") {

  import uk.gov.hmrc.helptosave.util.lock.LockSpec._

  val testLockID = "lockID"

  val lockDuration: FiniteDuration = 1.hour

  val time: VirtualTime = new VirtualTime

  trait TestableExclusiveTimePeriodLock extends LockProvider {

    override val lockId: String = testLockID

    override val holdLockFor = lockDuration

  }

  val internalLock = mock[TestableExclusiveTimePeriodLock]

  def sendToSelf[A](a: A): A = {
    self ! a
    a
  }

  // create a lock where the Int in the State increases by 1
  // each time the lock is acquired and decreases by 1 each time
  // the lock is released
  def newLock(time: VirtualTime): ActorRef = system.actorOf(Props(
    new Lock[State](
      internalLock,
      time.scheduler,
      State(0),
      s ⇒ sendToSelf(State(s.i + 1)),
      s ⇒ sendToSelf(State(s.i - 1)),
      { f ⇒ sendToSelf(StopHook(f)); () }
    )
  ))

  lazy val lock = newLock(time)

  def mockTryToAcquireOrRenewLock(result: Either[String, Option[Unit]]): Unit =
    (internalLock.tryToAcquireOrRenewLock(_: Future[Unit])(_: ExecutionContext))
      .expects(*, *)
      .returning(result.fold(e ⇒ Future.failed(new Exception(e)), Future.successful))

  def mockReleaseLock(result: Either[String, Unit]) =
    (internalLock.releaseLock: () ⇒ Future[Unit])
      .expects()
      .returning(result.fold(e ⇒ Future.failed(new Exception(e)), Future.successful))

  "The Lock" must {

      def startNewLock(mockActions: ⇒ Unit): (ActorRef, VirtualTime, StopHook) = {
        mockActions

        // start the actor
        val time: VirtualTime = new VirtualTime
        val lock = newLock(time)

        // the stop hook should be registered
        val hook = expectMsgType[StopHook]

        // now let it acquire the lock
        awaitActorReady(lock)

        (lock, time, hook)
      }

    "register an application lifecycle stop hook when starting which when triggered will release the lock if " +
      "acquired when triggered and change state if successful" in {
        val (_, time, hook) = startNewLock(
          inSequence {
            mockTryToAcquireOrRenewLock(Right(Some(())))
            mockReleaseLock(Right(()))
          })

        // expect the lock to be acquired
        time.advance(1L)
        expectMsg(State(1))

        // now actually trigger the stop hook to check  that the lock is released
        await(hook.f())
        // onRelease lock should be called if the release is successful
        expectMsg(State(0))
      }

    "register an application lifecycle stop hook when starting which when triggered will release the lock if " +
      "acquired when triggered and not change state if not successful" in {
        val (_, time, hook) = startNewLock(
          inSequence {
            mockTryToAcquireOrRenewLock(Right(Some(())))
            mockReleaseLock(Left(""))
          })

        // expect the lock to be acquired
        time.advance(1L)
        expectMsg(State(1))

        // now actually trigger the stop hook to check  that the lock is released
        await(hook.f())
        // onRelease lock should be called if the release is successful
        expectNoMessage()
      }

    "register an application lifecycle stop hook when starting which when triggered will do nothing if the " +
      "lock has not been acquired when triggered" in {
        val (_, _, hook) = startNewLock(())

        // trigger the stop hook to check that nothing happens
        await(hook.f())
        // onRelease lock should be called if the release is successful
        expectNoMessage()
      }

    "try to acquire the lock when started and change the state if " +
      "it is successful" in {
        mockTryToAcquireOrRenewLock(Right(Some(())))

        // even though the message is scheduled without delay we still
        // need to make the clock tick in order to get the scheduled task
        // to run
        awaitActorReady(lock)
        expectMsgType[StopHook]
        time.advance(1L)
        expectMsg(State(1))
      }

    "not try to renew the lock while the lock is still active" in {
      time.advance((lockDuration - 2.milli).toMillis)
      expectNoMessage()
    }

    "try to renew the lock when the lock expires amd change the state if " +
      "it is unsuccessful" in {
        mockTryToAcquireOrRenewLock(Right(None))

        time.advance(1)
        expectMsg(State(0))
      }

    "not change the state if there is an error while trying to acquire the lock" in {
      mockTryToAcquireOrRenewLock(Left(""))

      time.advance(lockDuration.toMillis)
      expectNoMessage()
    }

  }

}

object LockSpec {

  case class State(i: Int)

  case class StopHook(f: () ⇒ Future[Unit])

}
