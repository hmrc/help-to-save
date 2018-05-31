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

import cats.data.Validated.Valid
import uk.gov.hmrc.helptosave.utils.TestSupport

// scalastyle:off magic.number
class AccountSpec extends TestSupport {

  private val testNsiAccount = NsiAccount(
    accountClosedFlag      = "",
    accountBlockingCode    = "00",
    clientBlockingCode     = "00",
    accountBalance         = 0,
    currentInvestmentMonth = NsiCurrentInvestmentMonth(0, 0, LocalDate.of(1900, 1, 1)),
    terms                  = Seq.empty
  )

  val account = Account(false, Blocking(false), 123.45, 0, 0, 0, LocalDate.parse("1900-01-01"), List(), None, None)

  "Account class" when {
    "creating a new class from NsiAccount class" must {

      "return account details for open, unblocked account" in {
        val returnedAccount = Account(testNsiAccount.copy(accountBalance = BigDecimal("123.45")))
        returnedAccount shouldBe Valid(account)
      }

      """accept accountClosedFlag = " " (space) to mean not closed""" in {
        val returnedAccount = Account(testNsiAccount.copy(accountClosedFlag = " ", accountBalance = BigDecimal("123.45")))
        returnedAccount shouldBe Valid(account)
      }

      """return blocking.unspecified = true when accountBlockingCode is not "00"""" in {
        val returnedAccount = Account(testNsiAccount.copy(accountBlockingCode = "01"))
        returnedAccount shouldBe Valid(account.copy(blocked = Blocking(true), balance = 0))
      }

      "return an error for unknown accountClosedFlag values" in {
        val returnedAccount = Account(testNsiAccount.copy(accountClosedFlag = "O", accountBalance = BigDecimal("123.45")))
        returnedAccount.isInvalid shouldBe true
      }

      "return account details for closed account" in {
        val returnedAccount = Account(testNsiAccount.copy(
          accountClosedFlag     = "C",
          accountBalance        = BigDecimal("0.00"),
          accountClosureDate    = Some(LocalDate.of(2018, 2, 16)),
          accountClosingBalance = Some(BigDecimal("123.45"))
        ))

        returnedAccount shouldBe Valid(Account(true, Blocking(false), 0, 0, 0, 0, LocalDate.parse("1900-01-01"), List.empty, Some(LocalDate.of(2018, 2, 16)), Some(123.45)))
      }

      "return details for current month" in {

        val returnedAccount = Account(testNsiAccount.copy(
          currentInvestmentMonth = NsiCurrentInvestmentMonth(
            investmentRemaining = BigDecimal("12.34"),
            investmentLimit     = 50,
            endDate             = LocalDate.of(2019, 6, 23)
          )))

        returnedAccount shouldBe Valid(account.copy(balance                = 0, paidInThisMonth = 37.66, canPayInThisMonth = 12.34, maximumPaidInThisMonth = 50, thisMonthEndDate = LocalDate.of(2019, 6, 23)))
      }

      "return None when the payment amounts for current month don't make sense because investmentRemaining > investmentLimit" in {
        val returnedAccount = Account(testNsiAccount.copy(
          currentInvestmentMonth = testNsiAccount.currentInvestmentMonth.copy(investmentRemaining = BigDecimal("50.01"), investmentLimit = 50)))

        returnedAccount.isInvalid shouldBe true
      }

      "return payment amounts for current month when investmentRemaining == investmentLimit (boundary case for previous test)" in {
        val returnedAccount = Account(testNsiAccount.copy(
          currentInvestmentMonth = testNsiAccount.currentInvestmentMonth.copy(investmentRemaining = 50, investmentLimit = 50)))
        returnedAccount shouldBe Valid(account.copy(balance                = 0, paidInThisMonth = 0, canPayInThisMonth = 50, maximumPaidInThisMonth = 50))
      }

      "return bonus information including calculated bonusPaidOnOrAfterDate" in {
        val returnedAccount = Account(testNsiAccount.copy(
          terms = Seq(NsiBonusTerm(termNumber    = 1, endDate = LocalDate.of(2020, 10, 22), bonusEstimate = BigDecimal("65.43"), bonusPaid = 0))))

        val bonusTerms = BonusTerm(bonusEstimate          = BigDecimal("65.43"), bonusPaid = 0, endDate = LocalDate.of(2020, 10, 22), bonusPaidOnOrAfterDate = LocalDate.of(2020, 10, 23))
        returnedAccount shouldBe Valid(account.copy(balance    = 0, bonusTerms = List(bonusTerms)))
      }

      "sort the bonus terms by termNumber" in {
        val returnedAccount = Account(testNsiAccount.copy(
          accountBalance = BigDecimal("200.34"),
          terms          = Seq(
            NsiBonusTerm(termNumber    = 2, endDate = LocalDate.of(2021, 12, 31), bonusEstimate = 67, bonusPaid = 0),
            NsiBonusTerm(termNumber    = 1, endDate = LocalDate.of(2019, 12, 31), bonusEstimate = BigDecimal("123.45"), bonusPaid = BigDecimal("123.45"))
          )))

        val bonusTerm1 = BonusTerm(bonusEstimate          = BigDecimal("67"), bonusPaid = 0, endDate = LocalDate.of(2021, 12, 31), bonusPaidOnOrAfterDate = LocalDate.of(2022, 1, 1))
        val bonusTerm2 = BonusTerm(bonusEstimate          = BigDecimal("123.45"), bonusPaid = 123.45, endDate = LocalDate.of(2019, 12, 31), bonusPaidOnOrAfterDate = LocalDate.of(2020, 1, 1))
        returnedAccount shouldBe Valid(account.copy(balance    = 200.34, bonusTerms = List(bonusTerm2, bonusTerm1)))
      }
    }
  }

}
