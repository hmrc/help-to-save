/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsValue, Json}

case class ErrorResponse(errorMessageId: String, errorMessage: String, errorDetail: String)

object ErrorResponse {

  def apply(errorMessage: String, errorDetails: String): ErrorResponse =
    ErrorResponse("", errorMessage, errorDetails)

  implicit val format: Format[ErrorResponse] = Json.format[ErrorResponse]

  implicit class ErrorResponseOps(val errorResponse: ErrorResponse) extends AnyVal {
    def toJson(): JsValue = format.writes(errorResponse)
  }

}
