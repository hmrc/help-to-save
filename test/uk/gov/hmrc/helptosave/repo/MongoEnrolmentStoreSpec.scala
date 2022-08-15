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

package uk.gov.hmrc.helptosave.repo

import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.Await
import scala.concurrent.duration._

class MongoEnrolmentStoreSpec extends TestSupport with CleanMongoCollectionSupport {
  override def beforeAll(): Unit = {
    dropDatabase()
  }
  val repository: MongoEnrolmentStore = fakeApplication.injector.instanceOf[MongoEnrolmentStore]

  val ninoDifferentSuffix = "AE123456B"

  val accountNumber = "1234567890"

  def newMongoEnrolmentStore(mongoComponent: MongoComponent) =
    new MongoEnrolmentStore(mongoComponent, mockMetrics)

  def create(nino: NINO, itmpNeedsUpdate: Boolean, eligibilityReason: Option[Int], channel: String, store: MongoEnrolmentStore,
             accountNumber: Option[String]): Either[String, Unit] =
    Await.result(store.insert(nino, itmpNeedsUpdate, eligibilityReason, channel, accountNumber).value, 15.second)

  "The MongoEnrolmentStore" when {

    "creating" must {

      "create a new record in the db when inserted" in {
        val nino = randomNINO()
        val store = repository
        val create1 = create(nino, true, Some(7), "online", store, Some(accountNumber))
        create1 shouldBe Right(())
      }
    }

    "updating" must {

        def update(nino: NINO, itmpNeedsUpdate: Boolean, store: MongoEnrolmentStore): Either[String, Unit] =
          Await.result(store.updateItmpFlag(nino, itmpNeedsUpdate).value, 5.seconds)

      "update the mongodb collection" in {
        val nino = randomNINO()
        val store = repository
        create(nino, false, Some(7), "online", store, Some(accountNumber)) shouldBe Right(())
        update(nino, true, store) shouldBe Right(())
      }

      "return an error" when {

        "the future returned by mongo fails" in {
          val nino = randomNINO()
          val store = repository
          update(nino, false, store).isLeft shouldBe true
        }
      }

      "update the enrolment when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val store = repository
        create(nino, false, Some(7), "online", store, Some(accountNumber)) shouldBe Right(())
        update(ninoDifferentSuffix, true, store) shouldBe Right(())
      }
    }

    "getting" must {

        def get(nino: NINO, store: MongoEnrolmentStore): Either[String, Status] =
          Await.result(store.get(nino).value, 5.seconds)

      "attempt to find the entry in the collection based on the input nino" in {
        val nino = randomNINO()
        val store = repository
        get(nino, store) shouldBe Right(NotEnrolled)
      }

      "return an enrolled status if an entry is found" in {
        val nino = randomNINO()
        val store = repository
        create(nino, true, Some(7), "online", store, Some(accountNumber)) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(true))
      }

      "return a not enrolled status if the entry is not found" in {
        val nino = randomNINO()
        val store = repository
        get(nino, store) shouldBe Right(NotEnrolled)
      }

      "return an enrolled status when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val store = repository
        create(nino, true, Some(7), "online", store, Some(accountNumber)) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(true))
        get(ninoDifferentSuffix, store) shouldBe Right(Enrolled(true))
      }

    }

  }

}
