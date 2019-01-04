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

package uk.gov.hmrc.helptosave.models.account

import java.time.LocalDate

import play.api.libs.json.{Json, Reads}

case class NsiCurrentInvestmentMonth(
    investmentRemaining: BigDecimal,
    investmentLimit:     BigDecimal,
    endDate:             LocalDate
)

object NsiCurrentInvestmentMonth {
  implicit val reads: Reads[NsiCurrentInvestmentMonth] = Json.reads[NsiCurrentInvestmentMonth]
}

case class NsiBonusTerm(
    termNumber:    Int,
    startDate:     LocalDate,
    endDate:       LocalDate,
    bonusEstimate: BigDecimal,
    bonusPaid:     BigDecimal
)

object NsiBonusTerm {
  implicit val reads: Reads[NsiBonusTerm] = Json.reads[NsiBonusTerm]
}

case class NsiAccount(
    accountNumber:          String,
    accountClosedFlag:      String,
    accountBlockingCode:    String,
    clientBlockingCode:     String,
    accountBalance:         BigDecimal,
    currentInvestmentMonth: NsiCurrentInvestmentMonth,
    clientForename:         String,
    clientSurname:          String,
    emailAddress:           Option[String],
    terms:                  Seq[NsiBonusTerm],
    accountClosureDate:     Option[LocalDate]         = None,
    accountClosingBalance:  Option[BigDecimal]        = None
)

object NsiAccount {
  implicit val reads: Reads[NsiAccount] = Json.reads[NsiAccount]
}

