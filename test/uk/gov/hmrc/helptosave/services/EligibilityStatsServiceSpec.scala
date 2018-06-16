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

package uk.gov.hmrc.helptosave.services

import org.scalatest.EitherValues
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityStatsServiceSpec extends TestSupport with EitherValues {

  val eligibilityReportStore = mock[EligibilityStatsStore]

  val service = new EligibilityStatsServiceImpl(eligibilityReportStore, mockMetrics)

  def mockStats(result: Either[String, List[EligibilityStats]]) = {
    (eligibilityReportStore.getEligibilityStats: () ⇒ Future[List[EligibilityStats]]).expects()
      .returning(result.fold(
        e ⇒ Future.failed(new Exception(e)),
        r ⇒ Future.successful(r)
      ))
  }

  "The EligibilityStatsService" when {

    "returning the eligibility stats" must {

      val stats =
        List(
          EligibilityStats(Some(6), None, 1),
          EligibilityStats(Some(6), Some("Digital"), 1),
          EligibilityStats(Some(7), Some("Stride"), 2),
          EligibilityStats(Some(8), Some("KCOM"), 1),
          EligibilityStats(None, Some("Digital"), 1),
          EligibilityStats(None, None, 1)
        )

      val statsMessage =
        """
           |+--------+-------+-----+
           ||  Reason|Channel|Count|
           |+--------+-------+-----+
           ||      UC|Digital|    1|
           ||        | Stride|    0|
           ||        |   KCOM|    0|
           ||        |Unknown|    1|
           ||        |  Total|    2|
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
           ||  Unkown|Digital|    1|
           ||        | Stride|    0|
           ||        |   KCOM|    0|
           ||        |Unknown|    1|
           ||        |  Total|    2|
           ||        |       |     |
           ||  Totals|Digital|    2|
           ||        | Stride|    2|
           ||        |   KCOM|    1|
           ||        |Unknown|    2|
           ||        |  Total|    7|
           |+--------+-------+-----+""".stripMargin

        def result = Await.result(service.getEligibilityStats(), 5.seconds)

      "handle stats from mongo store" in {
        mockStats(Right(stats))
        result shouldBe Right(statsMessage)
      }

      "hanlde unexpected error during retreiving stats" in {
        mockStats(Left("future failed"))
        result shouldBe Left("future failed")
      }
    }
  }

}
