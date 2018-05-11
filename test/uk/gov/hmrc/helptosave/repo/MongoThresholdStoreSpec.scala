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

package uk.gov.hmrc.helptosave.repo

import reactivemongo.api.ReadPreference
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MongoThresholdStoreSpec extends TestSupport with MongoTestSupport[UCThreshold, MongoThresholdStore] {

  def newMongoStore() = new MongoThresholdStore(mockMongo) {

    override def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[UCThreshold]] =
      mockDBFunctions.findAll()

    override def doUpdate(amount: Double)(implicit ec: ExecutionContext): Future[Option[UCThreshold]] =
      mockDBFunctions.update(UCThreshold(amount))

  }

  "The MongoThresholdStore" when {
    val amount = 500.50

    "updating the threshold" must {

      val data = UCThreshold(amount)

        def update(amount: Double): Either[String, Unit] =
          Await.result(mongoStore.storeUCThreshold(amount).value, 5.seconds)

      "store the new threshold in the mongo database" in {
        mockUpdate(data)(Right(None))

        update(amount)
      }

      "return a right if the update is successful" in {
        mockUpdate(data)(Right(Some(data)))

        update(amount) shouldBe Right(())
      }

      "return a left if the update is unsuccessful" in {
        mockUpdate(data)(Right(None))

        update(amount).isLeft shouldBe true
      }

    }

    "getting the threshold from mongo" must {

        def get(): Either[String, Option[Double]] =
          Await.result(mongoStore.getUCThreshold().value, 5.seconds)

      "get the threshold currently held in mongo" in {
        mockFindAll()(Future.successful(List(UCThreshold(amount))))

        get()
      }

      "return a right if the get is successful" in {
        // try when there is a threshold
        mockFindAll()(Future.successful(List(UCThreshold(amount))))

        get shouldBe Right(Some(amount))

        // now try when there is no threshold
        mockFindAll()(Future.successful(List()))
        get shouldBe Right(None)
      }

      "return a left if the get is unsuccessful" in {
        mockFindAll()(Future.failed(new Exception("")))
        get.isLeft shouldBe true
      }

    }
  }

}
