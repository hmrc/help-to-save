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

package uk.gov.hmrc.helptosave.actors

import akka.pattern.ask
import akka.actor.{ActorIdentity, ActorRef, ActorSystem, Identify}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.Await
import scala.concurrent.duration._

class ActorTestSupport(name: String) extends TestKit(ActorSystem(
  name,
  ConfigFactory.defaultApplication().resolve()
    .withValue("akka.test.single-expect-default", ConfigValueFactory.fromAnyRef("5 seconds"))
)) with ImplicitSender with TestSupport {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  /**
   * In the tests we often need to use a mock VirtualTime to move forward time
   * in order to trigger some behaviour. If we create the actor and immediately after
   * move the time forward, the behaviour will not always be triggered because the actor
   * hasn't had a chance to schedule the messages it needs to send to itself to trigger
   * the behaviour yet. By waiting until the actor has replied an `Identify` message, we can
   * be sure that the actor has scheduled the messages.
   */
  def awaitActorReady(ref: ActorRef): ActorRef = {
    val msg = ref.ask(Identify(""))(4.seconds).mapTo[ActorIdentity]
    Await.result(msg, 3.seconds).ref.contains(ref) shouldBe true
    Thread.sleep(1000L)
    ref
  }

}

