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

package uk.gov.hmrc.helptosave.models.account

import java.time.LocalDate

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.eq._
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.util.Logging

case class BonusTerm(bonusEstimate:          BigDecimal,
                     bonusPaid:              BigDecimal,
                     endDate:                LocalDate,
                     bonusPaidOnOrAfterDate: LocalDate)

object BonusTerm {
  implicit val writes: Format[BonusTerm] = Json.format[BonusTerm]
}

case class Blocking(unspecified: Boolean)

object Blocking {
  implicit val writes: Format[Blocking] = Json.format[Blocking]
}

case class Account(isClosed:               Boolean,
                   blocked:                Blocking,
                   balance:                BigDecimal,
                   paidInThisMonth:        BigDecimal,
                   canPayInThisMonth:      BigDecimal,
                   maximumPaidInThisMonth: BigDecimal,
                   thisMonthEndDate:       LocalDate,
                   bonusTerms:             Seq[BonusTerm],
                   closureDate:            Option[LocalDate]  = None,
                   closingBalance:         Option[BigDecimal] = None)

object Account extends Logging {

  def apply(nsiAccount: NsiAccount): ValidatedNel[String, Account] = {
    val paidInThisMonthValidation: ValidOrErrorString[BigDecimal] = {
      val paidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit - nsiAccount.currentInvestmentMonth.investmentRemaining
      if (paidInThisMonth >= 0) {
        Valid(paidInThisMonth)
      } else {
        Invalid(NonEmptyList.one(s"investmentRemaining = ${nsiAccount.currentInvestmentMonth.investmentRemaining} and " +
          s"investmentLimit = ${nsiAccount.currentInvestmentMonth.investmentLimit} " +
          "values returned by NS&I don't make sense because they imply a negative amount paid in this month"
        ))
      }
    }

    val accountClosedValidation: ValidOrErrorString[Boolean] =
      if (nsiAccount.accountClosedFlag === "C") {
        Valid(true)
      } else if (nsiAccount.accountClosedFlag.trim.isEmpty) {
        Valid(false)
      } else {
        Invalid(NonEmptyList.one(s"""Unknown value for accountClosedFlag: "${nsiAccount.accountClosedFlag}""""))
      }

    (paidInThisMonthValidation, accountClosedValidation).mapN{
      case (paidInThisMonth, accountClosed) ⇒
        Account(
          isClosed               = accountClosed,
          blocked                = nsiAccountToBlocking(nsiAccount),
          balance                = nsiAccount.accountBalance,
          paidInThisMonth        = paidInThisMonth,
          canPayInThisMonth      = nsiAccount.currentInvestmentMonth.investmentRemaining,
          maximumPaidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit,
          thisMonthEndDate       = nsiAccount.currentInvestmentMonth.endDate,
          bonusTerms             = nsiAccount.terms.sortBy(_.termNumber).map(nsiBonusTermToBonusTerm),
          closureDate            = nsiAccount.accountClosureDate,
          closingBalance         = nsiAccount.accountClosingBalance
        )

    }
  }

  private type ValidOrErrorString[A] = ValidatedNel[String, A]

  private def nsiAccountToBlocking(nsiAccount: NsiAccount): Blocking = Blocking(
    unspecified = nsiAccount.accountBlockingCode != "00" || nsiAccount.clientBlockingCode != "00"
  )

  private def nsiBonusTermToBonusTerm(nsiBonusTerm: NsiBonusTerm): BonusTerm = BonusTerm(
    bonusEstimate          = nsiBonusTerm.bonusEstimate,
    bonusPaid              = nsiBonusTerm.bonusPaid,
    endDate                = nsiBonusTerm.endDate,
    bonusPaidOnOrAfterDate = nsiBonusTerm.endDate.plusDays(1)
  )

  implicit val format: Format[Account] = Json.format[Account]
}

