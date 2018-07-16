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

class EligibilityStatsParserSpec extends TestSupport with Matchers {

  val parser = new EligibilityStatsParserImpl()

  "The EligibilityParserHandler" when {

    "logging the eligibility stats" must {

      "handle stats returned from the scheduler" in {
        val message =
          """
            |+--------+----------+----------+
            || Reason | Channel  |  Count   |
            |+--------+----------+----------+
            ||      UC| BLAH BLAH|         0|
            ||        |   Digital|         1|
            ||        |      KCOM|         1|
            ||        |    Stride|         1|
            ||        |   Unknown|         1|
            ||        |     Total|         4|
            ||        |          |          |
            ||     WTC| BLAH BLAH|         0|
            ||        |   Digital|         0|
            ||        |      KCOM|         0|
            ||        |    Stride|         2|
            ||        |   Unknown|         0|
            ||        |     Total|         2|
            ||        |          |          |
            ||  UC&WTC| BLAH BLAH|         1|
            ||        |   Digital|         0|
            ||        |      KCOM|         1|
            ||        |    Stride|         0|
            ||        |   Unknown|         0|
            ||        |     Total|         2|
            ||        |          |          |
            || Unknown| BLAH BLAH|         0|
            ||        |   Digital|         1|
            ||        |      KCOM|         0|
            ||        |    Stride|         0|
            ||        |   Unknown|         1|
            ||        |     Total|         2|
            ||        |          |          |
            ||   Total| BLAH BLAH|         1|
            ||        |   Digital|         2|
            ||        |      KCOM|         2|
            ||        |    Stride|         3|
            ||        |   Unknown|         2|
            ||        |     Total|        10|
            |+--------+----------+----------+""".stripMargin

        parser.createTable(TestEligibilityStats.stats) shouldBe TestEligibilityStats.table
        parser.prettyFormatTable(TestEligibilityStats.table) shouldBe message
      }
    }
  }
}
