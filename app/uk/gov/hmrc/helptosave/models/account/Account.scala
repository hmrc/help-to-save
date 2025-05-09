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

package uk.gov.hmrc.helptosave.models.account

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.eq._
import play.api.libs.json._
import uk.gov.hmrc.helptosave.util.Logging

import java.time.{LocalDate, YearMonth}

case class BonusTerm(
  bonusEstimate: BigDecimal,
  bonusPaid: BigDecimal,
  startDate: LocalDate,
  endDate: LocalDate,
  bonusPaidOnOrAfterDate: LocalDate
)

object BonusTerm {
  implicit val writes: Format[BonusTerm] = Json.format[BonusTerm]
}

case class Blocking(payments: Boolean, withdrawals: Boolean, bonuses: Boolean)

object Blocking {
  implicit val writes: Format[Blocking] = Json.format[Blocking]
}

case class Account(
  openedYearMonth: YearMonth,
  accountNumber: String,
  isClosed: Boolean,
  blocked: Blocking,
  balance: BigDecimal,
  paidInThisMonth: BigDecimal,
  canPayInThisMonth: BigDecimal,
  maximumPaidInThisMonth: BigDecimal,
  thisMonthEndDate: LocalDate,
  accountHolderForename: String,
  accountHolderSurname: String,
  accountHolderEmail: Option[String],
  bonusTerms: Seq[BonusTerm],
  closureDate: Option[LocalDate] = None,
  closingBalance: Option[BigDecimal] = None,
  nbaAccountNumber: Option[String] = None,
  nbaPayee: Option[String] = None,
  nbaRollNumber: Option[String] = None,
  nbaSortCode: Option[String] = None
)

object Account extends Logging {

  def apply(nsiAccount: NsiAccount): ValidatedNel[String, Account] = {
    val paidInThisMonthValidation: ValidOrErrorString[BigDecimal] = {
      val paidInThisMonth =
        nsiAccount.currentInvestmentMonth.investmentLimit - nsiAccount.currentInvestmentMonth.investmentRemaining
      if paidInThisMonth >= 0 then {
        Valid(paidInThisMonth)
      } else {
        Invalid(
          NonEmptyList.one(
            s"investmentRemaining = ${nsiAccount.currentInvestmentMonth.investmentRemaining} and " +
              s"investmentLimit = ${nsiAccount.currentInvestmentMonth.investmentLimit} " +
              "values returned by NS&I don't make sense because they imply a negative amount paid in this month"
          )
        )
      }
    }

    val accountClosedValidation: ValidOrErrorString[Boolean] =
      if nsiAccount.accountClosedFlag === "C" then {
        Valid(true)
      } else if nsiAccount.accountClosedFlag.trim.isEmpty then {
        Valid(false)
      } else {
        Invalid(NonEmptyList.one(s"""Unknown value for accountClosedFlag: "${nsiAccount.accountClosedFlag}""""))
      }

    val sortedNsiTerms                                           = nsiAccount.terms.sortBy(_.termNumber)
    val openedYearMonthValidation: ValidOrErrorString[YearMonth] =
      sortedNsiTerms.headOption.fold[ValidOrErrorString[YearMonth]] {
        Invalid(NonEmptyList.of("Bonus terms list returned by NS&I was empty"))
      } { firstNsiTerm =>
        Valid(YearMonth.from(firstNsiTerm.startDate))
      }

    val blockingValidation: ValidOrErrorString[Blocking] = nsiAccountToBlockingValidation(nsiAccount)

    (paidInThisMonthValidation, accountClosedValidation, openedYearMonthValidation, blockingValidation).mapN {
      case (paidInThisMonth, accountClosed, openedYearMonth, blocking) =>
        Account(
          openedYearMonth = openedYearMonth,
          accountNumber = nsiAccount.accountNumber,
          isClosed = accountClosed,
          blocked = blocking,
          balance = nsiAccount.accountBalance,
          paidInThisMonth = paidInThisMonth,
          canPayInThisMonth = nsiAccount.currentInvestmentMonth.investmentRemaining,
          maximumPaidInThisMonth = nsiAccount.currentInvestmentMonth.investmentLimit,
          thisMonthEndDate = nsiAccount.currentInvestmentMonth.endDate,
          accountHolderForename = nsiAccount.clientForename,
          accountHolderSurname = nsiAccount.clientSurname,
          accountHolderEmail = nsiAccount.emailAddress,
          bonusTerms = sortedNsiTerms.map(nsiBonusTermToBonusTerm),
          closureDate = nsiAccount.accountClosureDate,
          closingBalance = nsiAccount.accountClosingBalance,
          nbaAccountNumber = nsiAccount.nbaAccountNumber,
          nbaPayee = nsiAccount.nbaPayee,
          nbaRollNumber = nsiAccount.nbaRollNumber,
          nbaSortCode = nsiAccount.nbaSortCode
        )

    }
  }

  private type ValidOrErrorString[A] = ValidatedNel[String, A]

  private val expectedBlockingCodes: Set[String] = Set("00", "11", "12", "13", "15", "30", "64")

  private def nsiAccountToBlockingValidation(nsiAccount: NsiAccount): ValidatedNel[String, Blocking] = {
    def checkIsValidCode(code: String): ValidOrErrorString[String] =
      if expectedBlockingCodes.contains(code) then { Valid(code) }
      else {
        Invalid(NonEmptyList.one(s"Received unexpected blocking code: $code"))
      }

    (checkIsValidCode(nsiAccount.accountBlockingCode), checkIsValidCode(nsiAccount.clientBlockingCode))
      .mapN { case (accountBlockingCode, clientBlockingCode) =>
        def isBlockedFromPredicate(predicate: String => Boolean): Boolean =
          predicate(accountBlockingCode) || predicate(clientBlockingCode)

        Blocking(
          payments = isBlockedFromPredicate(s => s =!= "00" && s =!= "11"),
          withdrawals = isBlockedFromPredicate(s => s =!= "00" && s =!= "12" && s =!= "15"),
          bonuses = isBlockedFromPredicate(s => s =!= "00" && s =!= "12")
        )
      }
  }

  private def nsiBonusTermToBonusTerm(nsiBonusTerm: NsiBonusTerm): BonusTerm = BonusTerm(
    bonusEstimate = nsiBonusTerm.bonusEstimate,
    bonusPaid = nsiBonusTerm.bonusPaid,
    startDate = nsiBonusTerm.startDate,
    endDate = nsiBonusTerm.endDate,
    bonusPaidOnOrAfterDate = nsiBonusTerm.endDate.plusDays(1)
  )

  implicit object YearMonthWrites extends Writes[YearMonth] {
    def writes(yearMonth: YearMonth): JsValue = JsString(yearMonth.toString)
  }

  implicit val writes: Writes[Account] = Json.writes[Account]
}
