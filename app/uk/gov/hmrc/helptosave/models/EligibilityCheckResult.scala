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
  * The result of performing an eligibility check. If the user is eligible
  * return their details, otherwise return [[None]]
  */
case class EligibilityCheckResult(result: Option[UserInfo])

object EligibilityCheckResult {
  implicit val eligibilityResultFormat: Format[EligibilityCheckResult] = Json.format[EligibilityCheckResult]
}