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

package uk.gov.hmrc.helptosave.models.bank

import play.api.libs.json.{Format, Json}

case class BankDetails(sortCode: String, accountNumber: String, rollNumber: Option[String], accountName: String)

object BankDetails {
  implicit val bankDetailsFormat: Format[BankDetails] = Json.format[BankDetails]
}

case class BankDetailsValidationRequest(nino: String, sortCode: String, accountNumber: String)

object BankDetailsValidationRequest {
  implicit val barsRequestFormat: Format[BankDetailsValidationRequest] = Json.format[BankDetailsValidationRequest]
}

case class BankDetailsValidationResult(isValid: Boolean, sortCodeExists: Boolean)

object BankDetailsValidationResult {
  implicit val format: Format[BankDetailsValidationResult] = Json.format[BankDetailsValidationResult]
}

case class Account(sortCode: String, accountNumber: String)
object Account {
  implicit val accountFormat: Format[Account] = Json.format[Account]
}

case class BarsRequest(account: Account)
object BarsRequest {
  implicit val barsRequestFormat: Format[BarsRequest] = Json.format[BarsRequest]
}
