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

import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats

object TestEligibilityStats {

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

  val table = Map(
    "8" -> Map(
      "BLAH BLAH" -> 1,
      "KCOM" -> 1,
      "Stride" -> 0,
      "Digital" -> 0,
      "Unknown" -> 0),
    "Unknown" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 0,
      "Stride" -> 0,
      "Digital" -> 1,
      "Unknown" -> 1),
    "7" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 0,
      "Stride" -> 2,
      "Digital" -> 0,
      "Unknown" -> 0),
    "6" -> Map(
      "BLAH BLAH" -> 0,
      "KCOM" -> 1,
      "Stride" -> 1,
      "Digital" -> 1,
      "Unknown" -> 1))

}
