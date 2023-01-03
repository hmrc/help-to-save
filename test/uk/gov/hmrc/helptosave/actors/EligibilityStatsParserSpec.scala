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

package uk.gov.hmrc.helptosave.actors

import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.utils.TestSupport

class EligibilityStatsParserSpec extends TestSupport {

  val parser = new EligibilityStatsParserImpl()

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
      EligibilityStats(None, None, 1),
      EligibilityStats(Some(3), Some("Stride-Manual"), 2)
    )

  val table = Map(
    "3" → Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 0,
      "Stride" -> 0,
      "Stride-Manual" → 2,
      "Digital" -> 0,
      "Unknown" -> 0
    ),
    "8" -> Map(
      "BLAH BLAH" -> 1,
      "KCOM" -> 1,
      "Stride" -> 0,
      "Stride-Manual" → 0,
      "Digital" -> 0,
      "Unknown" -> 0),
    "Unknown" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 0,
      "Stride" -> 0,
      "Stride-Manual" → 0,
      "Digital" -> 1,
      "Unknown" -> 1),
    "7" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 0,
      "Stride" -> 2,
      "Stride-Manual" → 0,
      "Digital" -> 0,
      "Unknown" -> 0),
    "6" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 1,
      "Stride" -> 1,
      "Stride-Manual" → 0,
      "Digital" -> 1,
      "Unknown" -> 1))

  "The EligibilityParserHandler" when {

    "logging the eligibility stats" must {

      "handle stats returned from the scheduler" in {
        val message =
          """|+--------+--------------+----------+
            ||  Reason|       Channel|     Count|
            |+--------+--------------+----------+
            ||       3|     BLAH BLAH|         0|
            ||        |       Digital|         0|
            ||        |          KCOM|         0|
            ||        |        Stride|         0|
            ||        | Stride-Manual|         2|
            ||        |       Unknown|         0|
            ||        |         Total|         2|
            ||        |              |          |
            ||      UC|     BLAH BLAH|         0|
            ||        |       Digital|         1|
            ||        |          KCOM|         1|
            ||        |        Stride|         1|
            ||        | Stride-Manual|         0|
            ||        |       Unknown|         1|
            ||        |         Total|         4|
            ||        |              |          |
            ||     WTC|     BLAH BLAH|         0|
            ||        |       Digital|         0|
            ||        |          KCOM|         0|
            ||        |        Stride|         2|
            ||        | Stride-Manual|         0|
            ||        |       Unknown|         0|
            ||        |         Total|         2|
            ||        |              |          |
            ||  UC&WTC|     BLAH BLAH|         1|
            ||        |       Digital|         0|
            ||        |          KCOM|         1|
            ||        |        Stride|         0|
            ||        | Stride-Manual|         0|
            ||        |       Unknown|         0|
            ||        |         Total|         2|
            ||        |              |          |
            || Unknown|     BLAH BLAH|         0|
            ||        |       Digital|         1|
            ||        |          KCOM|         0|
            ||        |        Stride|         0|
            ||        | Stride-Manual|         0|
            ||        |       Unknown|         1|
            ||        |         Total|         2|
            ||        |              |          |
            ||   Total|     BLAH BLAH|         1|
            ||        |       Digital|         2|
            ||        |          KCOM|         2|
            ||        |        Stride|         3|
            ||        | Stride-Manual|         2|
            ||        |       Unknown|         2|
            ||        |         Total|        12|
            |+--------+--------------+----------+""".stripMargin

        parser.createTable(stats) shouldBe table
        parser.prettyFormatTable(table) shouldBe message
      }
    }
  }
}
