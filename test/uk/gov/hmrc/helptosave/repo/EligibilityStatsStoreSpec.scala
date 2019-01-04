/*
 * Copyright 2019 HM Revenue & Customs
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
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.utils.TestSupport

class EligibilityStatsStoreSpec extends TestSupport with MongoSupport {

  def newEligibilityStatsMongoStore(reactiveMongoComponent: ReactiveMongoComponent) = new MongoEligibilityStatsStore(reactiveMongoComponent, mockMetrics)

  "The EligibilityStatsStore" when {

    "aggregating the eligibility stats" must {

      val document = Json.obj("eligibilityReason" -> 7, "source" -> "Digital", "total" -> 1).value

      "return results as expected" in {
        val store = newEligibilityStatsMongoStore(reactiveMongoComponent)

        await(store.collection.insert(document))
        await(store.getEligibilityStats()) shouldBe List(EligibilityStats(Some(7), Some("Digital"), 1))
      }
    }

    "handle error while reading from mongo" in {
      val store = newEligibilityStatsMongoStore(reactiveMongoComponent)

      await(store.getEligibilityStats()) shouldBe List.empty
    }
  }

}
