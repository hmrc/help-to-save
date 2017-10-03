/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.util.toFuture
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UserCapStoreSpec extends TestSupport with MongoTestSupport[UserCap, MongoUserCapStore] {

  override def newMongoStore() = new MongoUserCapStore(mockMongo) {

    override def indexes: Seq[Index] = {
      // this line is to ensure scoverage picks up this line in MongoUserCapStore -
      // we can't really test the indexes function, it doesn't affect the behaviour of
      // the class only its performance
      super.indexes
      Seq.empty[Index]
    }

    override def doFind(): Future[Option[UserCap]] = mockDBFunctions.getOne()

    override def doUpdate(userCap: UserCap): Future[Option[UserCap]] = mockDBFunctions.update(userCap)

    override def doRemove(userCap: UserCap): Future[Option[UserCap]] = mockDBFunctions.remove(userCap)
  }

  "The UserCapStore" when {

    val date = "2017-10-06"

    val record = UserCap(date, 1, 1)

    "getting the user-cap" should {

      "return the existing record successfully" in {

        mockGetOne()(toFuture(Some(record)))
        Await.result(mongoStore.getOne(), 5.seconds) shouldBe Some(record)
      }

      "returns None if no record exists" in {
        mockGetOne()(toFuture(None))
        Await.result(mongoStore.getOne(), 5.seconds) shouldBe None
      }
    }

    "updating the user-cap" should {

      "return the updated record successfully" in {

        mockUpdate(record)(Right(Some(record)))
        Await.result(mongoStore.upsert(record), 5.seconds) shouldBe Some(record)
      }
    }

    "removing the user-cap record" should {

      "delete the record successfully" in {

        mockDelete(record)(toFuture(Some(record)))
        Await.result(mongoStore.remove(record), 5.seconds) shouldBe Some(record)
      }
    }
  }

}
