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

import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import reactivemongo.play.json.collection.JSONBatchCommands.AggregationFramework
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityStatsStoreSpec extends TestSupport with MongoTestSupport[EnrolmentData, MongoEligibilityStatsStore] {

  def newMongoStore(): MongoEligibilityStatsStore = new MongoEligibilityStatsStore(mockMongo) {

    override def indexes: Seq[Index] = {
      // this line is to ensure scoverage picks up this line in MongoEnrolmentStore -
      // we can't really test the indexes function, it doesn't affect the behaviour of
      // the class only its performance
      super.indexes
      Seq.empty[Index]
    }

    override def doAggregate(): Future[AggregationFramework.AggregationResult] =
      mockDBFunctions.aggregate()
  }

  "The EligibilityStatsStore" when {

    "aggregating the eligibility stats" must {

      val documents = List(Json.obj("eligibilityReason" -> 7, "source" -> "Digital", "total" -> 1))

      "return results as expected" in {
        mockAggregate(Right(AggregationFramework.AggregationResult(documents)))
        Await.result(mongoStore.getEligibilityStats(), 5.seconds) shouldBe List(EligibilityStats(Some(7), Some("Digital"), 1))
      }
    }
  }
}
