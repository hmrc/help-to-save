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

package uk.gov.hmrc.helptosave.actors

import org.scalatest.Matchers
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.utils.TestSupport

class EligibilityStatsHandlerSpec extends TestSupport with Matchers {

  val mockLogger = mock[play.api.Logger]

  val handler = new EligibilityStatsHandlerImpl()

  "The EligibilityStatsHandler" when {

    "logging the eligibility stats" must {

      "handle stats returned from the scheduler" in {
        val stats =
          List(
            EligibilityStats(Some(6), None, 1),
            EligibilityStats(Some(6), Some("Stride"), 1),
            EligibilityStats(Some(6), Some("Digital"), 1),
            EligibilityStats(Some(6), Some("KCOM"), 1),
            EligibilityStats(Some(7), Some("Stride"), 2),
            EligibilityStats(Some(8), Some("KCOM"), 1),
            EligibilityStats(None, Some("Digital"), 1),
            EligibilityStats(Some(8), Some("BLAH BLAH"), 1),
            EligibilityStats(None, None, 1)
          )

        val message =
          """
         |+--------+----------+----------+
         || Reason | Channel  |  Count   |
         |+--------+----------+----------+
         ||      UC| BLAH BLAH|         0|
         ||      UC|   Digital|         1|
         ||      UC|      KCOM|         1|
         ||      UC|    Stride|         1|
         ||      UC|   Unknown|         1|
         ||        |     Total|         4|
         ||        |          |          |
         ||     WTC| BLAH BLAH|         0|
         ||     WTC|   Digital|         0|
         ||     WTC|      KCOM|         0|
         ||     WTC|    Stride|         2|
         ||     WTC|   Unknown|         0|
         ||        |     Total|         2|
         ||        |          |          |
         ||  UC&WTC| BLAH BLAH|         1|
         ||  UC&WTC|   Digital|         0|
         ||  UC&WTC|      KCOM|         1|
         ||  UC&WTC|    Stride|         0|
         ||  UC&WTC|   Unknown|         0|
         ||        |     Total|         2|
         ||        |          |          |
         || Unknown| BLAH BLAH|         0|
         || Unknown|   Digital|         1|
         || Unknown|      KCOM|         0|
         || Unknown|    Stride|         0|
         || Unknown|   Unknown|         1|
         ||        |     Total|         2|
         ||        |          |          |
         ||   Total| BLAH BLAH|         1|
         ||   Total|   Digital|         2|
         ||   Total|      KCOM|         2|
         ||   Total|    Stride|         3|
         ||   Total|   Unknown|         2|
         ||        |     Total|        10|
         |+--------+----------+----------+""".stripMargin

        handler.handleStats(stats) shouldBe message
      }
    }
  }
}
