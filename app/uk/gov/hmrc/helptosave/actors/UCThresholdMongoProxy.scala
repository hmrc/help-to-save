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

import akka.pattern.pipe
import akka.actor.{Actor, Props}
import cats.syntax.either._
import uk.gov.hmrc.helptosave.actors.UCThresholdMongoProxy._
import uk.gov.hmrc.helptosave.repo.ThresholdStore

class UCThresholdMongoProxy(thresholdStore: ThresholdStore) extends Actor {
  import context.dispatcher

  def retrieveThreshold() = thresholdStore.getUCThreshold().value

  def storeThreshold(amount: Double) = thresholdStore.storeUCThreshold(amount).value

  override def receive: Receive = {
    case GetThresholdValue ⇒
      retrieveThreshold().map(GetThresholdValueResponse) pipeTo sender
    case StoreThresholdValue(amount) ⇒
      val result = storeThreshold(amount).map(r ⇒ StoreThresholdValueResponse(r.map(_ ⇒ amount)))
      result pipeTo sender
  }

}

object UCThresholdMongoProxy {

  case object GetThresholdValue

  case class StoreThresholdValue(amount: Double)

  case class GetThresholdValueResponse(result: Either[String, Option[Double]])

  case class StoreThresholdValueResponse(result: Either[String, Double])

  def props(thresholdStore: ThresholdStore): Props =
    Props(new UCThresholdMongoProxy(thresholdStore))

}
