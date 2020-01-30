/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDate, ZoneId}

import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.utils.TestSupport

class UserCapStoreSpec extends TestSupport with MongoSupport {

  def newUserCapMongoStore(reactiveMongoComponent: ReactiveMongoComponent) =
    new MongoUserCapStore(reactiveMongoComponent)

  "The UserCapStore" when {

    val record = UserCap(LocalDate.now(ZoneId.of("Z")), 1, 1)

    "getting the user-cap" should {

      "return the existing record successfully" in {
        val store = newUserCapMongoStore(reactiveMongoComponent)
        await(store.doUpdate(record))
        await(store.get()) shouldBe Some(record)
      }

      "returns None if no record exists" in {
        val store = newUserCapMongoStore(reactiveMongoComponent)
        await(store.get()) shouldBe None
      }
    }
  }

}
