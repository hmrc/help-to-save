/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.testkit.TestProbe
import com.miguno.akka.testing.VirtualTime
import uk.gov.hmrc.helptosave.actors.ActorTestSupport
import uk.gov.hmrc.helptosave.util.WithExponentialBackoffRetry.ExponentialBackoffRetry

import scala.concurrent.duration._

class ExponentialBackoffRetrySpec extends ActorTestSupport("ExponentialBackoffRetrySpec") {

  case class TestMessage(s: String)

  // if we have an exponential increase rate which tends towards 5 seconds, then the difference
  // between consecutive retry times should decrease with time
  def testRetryTimes(retryTimes: List[FiniteDuration]) = {
      def loop(
          previousPreviousTime: FiniteDuration,
          previousTime:         FiniteDuration,
          list:                 List[FiniteDuration]
      ): List[FiniteDuration] = list match {
        case Nil ⇒ list
        case head :: tail ⇒
          (previousTime > previousPreviousTime) shouldBe true
          (head > previousTime) shouldBe true
          ((previousTime - previousPreviousTime) > (head - previousTime)) shouldBe true
          tail
      }

    retryTimes match {
      case Nil | _ :: Nil   ⇒ fail(s"unexpected number of elements in list: ${retryTimes.length}. Expected 2 or greater.")
      case t1 :: t2 :: tail ⇒ loop(t1, t2, tail)
    }
  }

  "ExponentialBackoffRetry" must {

    "schedule retry message at an rate which starts at the configured minimum and " +
      "tends towards the configured maximum at an exponential rate" in {
        val probe = TestProbe()
        val time = new VirtualTime()

        val exponentialBackoffRetry = ExponentialBackoffRetry(
          1.second,
          5.seconds,
          0.4,
          probe.ref,
          { TestMessage.apply _ },
          time.scheduler
        )

        val result: List[FiniteDuration] = (1 to 10).foldLeft(List.empty[FiniteDuration]){
          case (acc, curr) ⇒
            exponentialBackoffRetry.retry(curr.toString()).fold[List[FiniteDuration]](
              fail("Retry time was not defined")
            ){ t ⇒
                time.advance(t)
                probe.expectMsg(TestMessage(curr.toString()))
                t :: acc
              }
        }.reverse

        testRetryTimes(result)

        // create a retry job but cancel it before i triggers
        exponentialBackoffRetry.retry("x")
        exponentialBackoffRetry.cancelAndReset()

        exponentialBackoffRetry.retry("1")
        time.advance(1.second)
        probe.expectMsg(TestMessage("1"))
      }

    "have an apply method which takes in an integer constant which makes the retry time double " +
      "after the given integer constant" in {
        val probe = TestProbe()
        val time = new VirtualTime()
        val n = 10

        val exponentialBackoffRetry = ExponentialBackoffRetry(
          1.second,
          5.seconds,
          n,
          probe.ref,
          { TestMessage.apply _ },
          time.scheduler
        )

        (1 to n).foreach{ i ⇒
          exponentialBackoffRetry.retry("")
          // advance 1 day to ensure scheduled send actually completes
          time.advance(1.day)
          probe.expectMsg(TestMessage(""))
        }

        // retry time should now be double the initial retry time
        exponentialBackoffRetry.retry("a")
        time.advance(2.seconds - 1.milli)
        probe.expectNoMessage()
        time.advance(1.milli)
        probe.expectMsg(TestMessage("a"))
      }

    "not schedule a retry if there already is a retry scheduled" in {
      val probe = TestProbe()
      val time = new VirtualTime()

      val exponentialBackoffRetry = ExponentialBackoffRetry(
        1.second,
        3.second,
        5,
        probe.ref,
        { TestMessage.apply _ },
        time.scheduler
      )

      exponentialBackoffRetry.retry("")

      time.advance(1.second)
      probe.expectMsg(TestMessage(""))

      // check no other retries happen
      time.advance(1.day)
      probe.expectNoMessage()
    }

    "return the correct isActive status" in {
      val time = new VirtualTime()
      val probe = TestProbe()

      val exponentialBackoffRetry = ExponentialBackoffRetry(
        1.second,
        5.seconds,
        1.0,
        probe.ref,
        { TestMessage.apply _ },
        time.scheduler
      )

      exponentialBackoffRetry.isActive shouldBe false

      exponentialBackoffRetry.retry("")
      exponentialBackoffRetry.isActive shouldBe true

      time.advance(1.second)
      probe.expectMsg(TestMessage(""))
      exponentialBackoffRetry.isActive shouldBe false

      exponentialBackoffRetry.retry("")
      exponentialBackoffRetry.isActive shouldBe true

      exponentialBackoffRetry.cancelAndReset()
      exponentialBackoffRetry.isActive shouldBe false

    }

  }

}
