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

package uk.gov.hmrc.helptosave.repo

import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.{LocalDate, ZoneId}

class UserCapStoreSpec extends TestSupport with MongoSupport {

  val repository: MongoUserCapStore = newUserCapStore(mongoComponent)
  override def beforeEach(): Unit =
    await(repository.collection.drop().toFuture())
  def newUserCapStore(mongoComponent: MongoComponent) = new MongoUserCapStore(mongoComponent, appConfig)

  "The UserCapStore" when {

    val record = UserCap(LocalDate.now(ZoneId.of("Z")), 1, 1)

    "getting the user-cap" should {

      "return the existing record successfully" in {
        val store = repository

        await(store.doUpdate(record))
        await(store.get()) shouldBe Some(record)
      }

      "returns None if no record exists" in {
        val store = repository
        await(store.get()) shouldBe None
      }
    }
  }

}
