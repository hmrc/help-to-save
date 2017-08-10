/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models

import play.api.libs.json.{Format, Json}

/**
  * Response from ITMP eligibility check
  *
  * @param result 1 = customer eligible to HtS Account
  *               2 = customer ineligible to HtS Account
  * @param reason 1 = An HtS account was opened previously (the HtS account may have been closed or inactive)
  *               2 = Not entitled to WTC and not in receipt of UC
  *               3 = Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC)   and not in receipt of UC
  *               4 = Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC)   and in receipt of UC but income is insufficient
  *               5 = Not entitled to WTC and in receipt of UC but income is insufficient
  *               6 = In receipt of UC and income sufficient
  *               7 = Entitled to WTC and in receipt of positive WTC/CTC Tax Credit
  *               8 = Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient
  *               N.B. 1-5 represent reasons for ineligibility and 6-8 repesents reasons for eligibility
  */
case class EligibilityCheckResult(result: Int, reason: Int)

object EligibilityCheckResult {

  implicit val format: Format[EligibilityCheckResult] = Json.format[EligibilityCheckResult]

}