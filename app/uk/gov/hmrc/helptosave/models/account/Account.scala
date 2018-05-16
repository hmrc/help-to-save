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

import cats.instances.string._
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

  def apply(nsiAccount: NsiAccount): Option[Account] = {
    val paidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit - nsiAccount.currentInvestmentMonth.investmentRemaining
    if (paidInThisMonth >= 0) {
      Some(Account(
        isClosed               = nsiAccountClosedFlagToIsClosed(nsiAccount.accountClosedFlag),
        blocked                = nsiAccountToBlocking(nsiAccount),
        balance                = nsiAccount.accountBalance,
        paidInThisMonth        = paidInThisMonth,
        canPayInThisMonth      = nsiAccount.currentInvestmentMonth.investmentRemaining,
        maximumPaidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit,
        thisMonthEndDate       = nsiAccount.currentInvestmentMonth.endDate,
        bonusTerms             = nsiAccount.terms.sortBy(_.termNumber).map(nsiBonusTermToBonusTerm),
        closureDate            = nsiAccount.accountClosureDate,
        closingBalance         = nsiAccount.accountClosingBalance
      ))
    } else {
      // investmentRemaining is unaffected by debits (only credits) so should never exceed investmentLimit
      logger.warn(
        s"investmentRemaining = ${nsiAccount.currentInvestmentMonth.investmentRemaining} and investmentLimit = ${nsiAccount.currentInvestmentMonth.investmentLimit} " +
          "values returned by NS&I don't make sense because they imply a negative amount paid in this month"
      )
      None
    }
  }

  private def nsiAccountClosedFlagToIsClosed(accountClosedFlag: String): Boolean =
    if (accountClosedFlag === "C") {
      true
    } else if (accountClosedFlag.trim.isEmpty) {
      false
    } else {
      logger.warn(s"""Unknown value for accountClosedFlag: "$accountClosedFlag"""")
      false
    }

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
