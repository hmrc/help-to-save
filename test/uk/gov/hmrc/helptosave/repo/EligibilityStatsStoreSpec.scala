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

import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import org.mongodb.scala.ObservableFuture

class EligibilityStatsStoreSpec extends TestSupport with MongoSupport with BeforeAndAfterEach {

  def newEligibilityStatsMongoStore(mongoComponent: MongoComponent) =
    new MongoEligibilityStatsStore(mongoComponent, mockMetrics)
  val repository: MongoEligibilityStatsStore = newEligibilityStatsMongoStore(mongoComponent)

  override def beforeEach(): Unit =
    //    await(repository.collection.drop().toFuture())
    dropDatabase()

  "The EligibilityStatsStore" when {

    "aggregating the eligibility stats" must {

      "return results as expected" in {

        await(
          repository.collection
            .insertOne(
              EnrolmentData(
                nino = randomNINO(),
                itmpHtSFlag = false,
                eligibilityReason = Some(7),
                source = Some("Digital")))
            .toFuture())
        await(repository.getEligibilityStats) shouldBe List(EligibilityStats(Some(7), Some("Digital"), 1))
      }
    }

    "handle error while reading from mongo" in {

      await(repository.getEligibilityStats) shouldBe List.empty
    }

    "return aggregated results when there is more than one result" in {
      val enrolmentData = EnrolmentData(
        nino = randomNINO(),
        itmpHtSFlag = false,
        eligibilityReason = Some(7),
        source = Some("Digital")
      )

      await(repository.collection.insertOne(enrolmentData).toFuture())

      await(repository.collection.insertOne(enrolmentData).toFuture())

      await(
        repository.collection
          .insertOne(
            EnrolmentData(
              nino = randomNINO(),
              itmpHtSFlag = false,
              eligibilityReason = Some(8),
              source = Some("Digital")
            )
          )
          .toFuture())

      await(repository.getEligibilityStats).sortBy(_.eligibilityReason) shouldBe List(
        EligibilityStats(Some(7), Some("Digital"), 2),
        EligibilityStats(Some(8), Some("Digital"), 1)
      ).sortBy(_.eligibilityReason)
    }
  }

}
