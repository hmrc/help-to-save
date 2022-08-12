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

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

class EligibilityStatsStoreSpec extends TestSupport with CleanMongoCollectionSupport {

  def newEligibilityStatsMongoStore(mongoComponent: MongoComponent) = new MongoEligibilityStatsStore(mongoComponent, mockMetrics)

  "The EligibilityStatsStore" when {

    val document = Json.obj("eligibilityReason" -> 7, "source" -> "Digital", "total" -> 1).value

    "aggregating the eligibility stats" must {

      "return results as expected" in {
        val repository = newEligibilityStatsMongoStore(mongoComponent)

        await(repository.collection.insertOne(EnrolmentData(nino              = randomNINO(), itmpHtSFlag = false, eligibilityReason = Some(7), source = Some("Digital"))).toFuture())
        await(repository.getEligibilityStats) shouldBe List(EligibilityStats(Some(7), Some("Digital"), 1))
      }
    }

    "handle error while reading from mongo" in {
      val repository = newEligibilityStatsMongoStore(mongoComponent)

      await(repository.getEligibilityStats) shouldBe List.empty
    }

    "return aggregated results when there is more than one result" in {
      val document2 = Json.obj("eligibilityReason" -> 7, "source" -> "Digital", "total" -> 1).value
      val document3 = Json.obj("eligibilityReason" -> 8, "source" -> "Digital", "total" -> 1).value
      val repository = newEligibilityStatsMongoStore(mongoComponent)

      //      await(store.collection.insert(ordered = false).one(document))
      //      await(store.collection.insert(ordered = false).one(document2))
      //      await(store.collection.insert(ordered = false).one(document3))
      await(repository.collection.insertOne(
        EnrolmentData(
          nino              = randomNINO(),
          itmpHtSFlag       = false,
          eligibilityReason = Some(7),
          source            = Some("Digital")
        )
      ).toFuture())

      await(repository.collection.insertOne(
        EnrolmentData(
          nino              = randomNINO(),
          itmpHtSFlag       = false,
          eligibilityReason = Some(7),
          source            = Some("Digital")
        )
      ).toFuture())

      await(repository.collection.insertOne(
        EnrolmentData(
          nino              = randomNINO(),
          itmpHtSFlag       = false,
          eligibilityReason = Some(8),
          source            = Some("Digital")
        )
      ).toFuture())

      await(repository.getEligibilityStats) shouldBe List(
        EligibilityStats(Some(7), Some("Digital"), 2),
        EligibilityStats(Some(8), Some("Digital"), 1)
      )
    }
  }

}
