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

package uk.gov.hmrc.helptosaveeligibilitycheck.models

import play.api.libs.json.{Format, JsResult, JsValue, Json}

case class EligibilityResult(value: Option[UserDetails]) extends AnyVal

object EligibilityResult {
  implicit val format: Format[EligibilityResult] = new Format[EligibilityResult]{
    implicit val optionUserFormat: Format[Option[UserDetails]] = Format.optionWithNull[UserDetails]

    override def writes(o: EligibilityResult): JsValue =
      Json.toJson(o.value)

    override def reads(json: JsValue): JsResult[EligibilityResult] =
      Json.fromJson[Option[UserDetails]](json).map(new EligibilityResult(_))
  }
}
