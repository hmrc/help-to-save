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

import cats.data.EitherT
import cats.instances.future._
import uk.gov.hmrc.helptosave.repo.ThresholdStore

import scala.concurrent.{ExecutionContext, Future}

class ThresholdMongoProxySpec extends ActorTestSupport("ThresholdMongoProxySpec") {
  import system.dispatcher

  val thresholdStore = mock[ThresholdStore]

  val actor = system.actorOf(ThresholdMongoProxy.props(thresholdStore))

  def mockGetValueFromMongo(result: Either[String, Option[Double]]) =
    (thresholdStore.getThreshold()(_: ExecutionContext))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockStoreValueInMongo(amount: Double)(result: Either[String, Unit]) =
    (thresholdStore.storeThreshold(_: Double)(_: ExecutionContext))
      .expects(amount, *)
      .returning(EitherT.fromEither[Future](result))

  "The ThresholdMongoProxy" when {

    "asked for the threshold" must {
      "return a value from mongo successfully if there is a threshold value stored in mongo" in {
        mockGetValueFromMongo(Right(Some(100.0)))

        actor ! ThresholdMongoProxy.GetThresholdValue
        expectMsg(ThresholdMongoProxy.GetThresholdValueResponse(Right(Some(100.0))))
      }

      "return None from mongo if there is no threshold value stored" in {
        mockGetValueFromMongo(Right(None))

        actor ! ThresholdMongoProxy.GetThresholdValue
        expectMsg(ThresholdMongoProxy.GetThresholdValueResponse(Right(None)))
      }

      "return a Left if the value has not been retrieved from mongo" in {
        mockGetValueFromMongo(Left("An error occurred"))

        actor ! ThresholdMongoProxy.GetThresholdValue
        expectMsg(ThresholdMongoProxy.GetThresholdValueResponse(Left("An error occurred")))
      }

    }

    "asked to store the threshold" must {
      "return a Right if the value that has been stored successfully in mongo" in {
        mockStoreValueInMongo(100.0)(Right(()))

        actor ! ThresholdMongoProxy.StoreThresholdValue(100.0)
        expectMsg(ThresholdMongoProxy.StoreThresholdValueResponse(Right(100.0)))
      }

      "return a Left if the value has not been stored in mongo" in {
        mockStoreValueInMongo(100.0)(Left("An error occurred"))

        actor ! ThresholdMongoProxy.StoreThresholdValue(100.0)
        expectMsg(ThresholdMongoProxy.StoreThresholdValueResponse(Left("An error occurred")))
      }
    }

  }
}
