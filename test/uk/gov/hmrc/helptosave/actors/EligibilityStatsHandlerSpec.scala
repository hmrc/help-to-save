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
            EligibilityStats(None, None, 1)
          )

        val message =
          """
            |+--------+-------+-----+
            ||  Reason|Channel|Count|
            |+--------+-------+-----+
            ||      UC|Digital|    1|
            ||        | Stride|    1|
            ||        |   KCOM|    1|
            ||        |Unknown|    1|
            ||        |  Total|    4|
            ||        |       |     |
            ||     WTC|Digital|    0|
            ||        | Stride|    2|
            ||        |   KCOM|    0|
            ||        |Unknown|    0|
            ||        |  Total|    2|
            ||        |       |     |
            ||UC & WTC|Digital|    0|
            ||        | Stride|    0|
            ||        |   KCOM|    1|
            ||        |Unknown|    0|
            ||        |  Total|    1|
            ||        |       |     |
            || Unknown|Digital|    1|
            ||        | Stride|    0|
            ||        |   KCOM|    0|
            ||        |Unknown|    1|
            ||        |  Total|    2|
            ||        |       |     |
            ||  Totals|Digital|    2|
            ||        | Stride|    3|
            ||        |   KCOM|    2|
            ||        |Unknown|    2|
            ||        |  Total|    9|
            |+--------+-------+-----+
          """.stripMargin

        handler.handleStats(stats) shouldBe message
      }
    }
  }
}
