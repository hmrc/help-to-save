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

import com.github.simplyscala.MongoEmbedDatabase
import org.scalatest.concurrent.Eventually
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.Await
import scala.concurrent.duration._

class MongoEnrolmentStoreSpec extends TestSupport with MongoEmbedDatabase with Eventually with MongoSupport {

  def newMongoEnrolmentStore(reactiveMongoComponent: ReactiveMongoComponent) =
    new MongoEnrolmentStore(reactiveMongoComponent, mockMetrics)

  def create(nino: NINO, itmpNeedsUpdate: Boolean, eligibilityReason: Option[Int], channel: String, store: MongoEnrolmentStore): Either[String, Unit] =
    Await.result(store.insert(nino, itmpNeedsUpdate, eligibilityReason, channel).value, 5.seconds)

  "The MongoEnrolmentStore" when {

    val nino = "NINO"

    "creating" must {

      "create a new record in the db when inserted" in {
        withMongo { reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          create(nino, true, Some(7), "online", store) shouldBe Right(())
        }
      }

      "return an error" when {

        "the future returned by mongo fails" in {
          withBrokenMongo { reactiveMongoComponent ⇒
            val store = newMongoEnrolmentStore(reactiveMongoComponent)
            create(nino, true, Some(7), "online", store).isLeft shouldBe true
          }
        }
      }
    }

    "updating" must {

        def update(nino: NINO, itmpNeedsUpdate: Boolean, store: MongoEnrolmentStore): Either[String, Unit] =
          Await.result(store.update(nino, itmpNeedsUpdate).value, 5.seconds)

      "update the mongodb collection" in {
        withMongo{ reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          create(nino, false, Some(7), "online", store) shouldBe Right(())
          update(nino, true, store) shouldBe Right(())
        }
      }

      "return an error" when {

        "the future returned by mongo fails" in {
          withBrokenMongo { reactiveMongoComponent ⇒
            val store = newMongoEnrolmentStore(reactiveMongoComponent)
            update(nino, false, store).isLeft shouldBe true
          }

        }
      }
    }

    "getting" must {

        def get(nino: NINO, store: MongoEnrolmentStore): Either[String, Status] =
          Await.result(store.get(nino).value, 5.seconds)

      "attempt to find the entry in the collection based on the input nino" in {
        withMongo { reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          get(nino, store) shouldBe Right(NotEnrolled)
        }
      }

      "return an enrolled status if an entry is found" in {
        withMongo { reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          create(nino, true, Some(7), "online", store) shouldBe Right(())
          get(nino, store) shouldBe Right(Enrolled(true))
        }
      }

      "return a not enrolled status if the entry is not found" in {
        withMongo { reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          get(nino, store) shouldBe Right(NotEnrolled)
        }
      }

      "return an error if there is an error while finding the entry" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val store = newMongoEnrolmentStore(reactiveMongoComponent)
          get(nino, store).isLeft shouldBe true
        }
      }
    }

  }

}
