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
import uk.gov.hmrc.helptosave.utils.TestSupport

import java.time.{LocalDate, YearMonth}

// scalastyle:off magic.number
class AccountSpec extends TestSupport {

  private val testNsiAccount = NsiAccount(
    accountClosedFlag = "",
    accountBlockingCode = "00",
    clientBlockingCode = "00",
    accountBalance = 0,
    currentInvestmentMonth = NsiCurrentInvestmentMonth(0, 0, LocalDate.of(2018, 1, 31)),
    clientForename = "Testforename",
    clientSurname = "Testsurname",
    emailAddress = Some("test@example.com"),
    terms = Seq(
      NsiBonusTerm(
        termNumber = 1,
        startDate = LocalDate.of(2018, 1, 1),
        endDate = LocalDate.of(2019, 12, 31),
        bonusEstimate = 0,
        bonusPaid = 0
      ),
      NsiBonusTerm(
        termNumber = 2,
        startDate = LocalDate.of(2020, 1, 1),
        endDate = LocalDate.of(2021, 12, 31),
        bonusEstimate = 0,
        bonusPaid = 0
      )
    ),
    accountNumber = "AC01",
    accountClosureDate = None,
    accountClosingBalance = None
  )

  val account: Account = Account(
    YearMonth.of(2018, 1),
    "AC01",
    isClosed = false,
    Blocking(payments = false, withdrawals = false, bonuses = false),
    123.45,
    0,
    0,
    0,
    LocalDate.of(2018, 1, 31),
    accountHolderForename = "Testforename",
    accountHolderSurname = "Testsurname",
    accountHolderEmail = Some("test@example.com"),
    Seq(
      BonusTerm(
        startDate = LocalDate.of(2018, 1, 1),
        endDate = LocalDate.of(2019, 12, 31),
        bonusEstimate = 0,
        bonusPaid = 0,
        bonusPaidOnOrAfterDate = LocalDate.of(2020, 1, 1)
      ),
      BonusTerm(
        startDate = LocalDate.of(2020, 1, 1),
        endDate = LocalDate.of(2021, 12, 31),
        bonusEstimate = 0,
        bonusPaid = 0,
        bonusPaidOnOrAfterDate = LocalDate.of(2022, 1, 1)
      )
    ),
    None,
    None
  )

  "Account class" when {
    "creating a new class from NsiAccount class" must {

      "return account details for open, unblocked account" in {
        val returnedAccount = Account(testNsiAccount.copy(accountBalance = BigDecimal("123.45")))
        returnedAccount shouldBe Valid(account)
      }

      """accept accountClosedFlag = " " (space) to mean not closed""" in {
        val returnedAccount =
          Account(testNsiAccount.copy(accountClosedFlag = " ", accountBalance = BigDecimal("123.45")))
        returnedAccount shouldBe Valid(account)
      }

      def testBlockingCodes(codes: String*)(assertion: ValidatedNel[String, Account] => Unit) =
        codes.foreach { code =>
          List(
            testNsiAccount.copy(accountBlockingCode = code),
            testNsiAccount.copy(clientBlockingCode = code),
            testNsiAccount.copy(accountBlockingCode = code, clientBlockingCode = code)
          ).foreach { nsiAccount =>
            withClue(s"For NsiAccount: $nsiAccount")(assertion(Account(nsiAccount)))
          }
        }

      """return blocking.payments = true when accountBlockingCode or clientBlockingCode is not "00" or "11""" in {
        testBlockingCodes("12", "13", "15", "30", "64") {
          _.map(_.blocked.payments) shouldBe Valid(true)
        }
      }

      """return blocking.payments = false when accountBlockingCode or clientBlockingCode is "00" or "11"""" in {
        testBlockingCodes("00", "11") {
          _.map(_.blocked.payments) shouldBe Valid(false)
        }
      }

      """return blocking.withdrawals = true when accountBlockingCode or clientBlockingCode is not "00" or "12" or "15""" in {
        testBlockingCodes("11", "13", "30", "64") {
          _.map(_.blocked.withdrawals) shouldBe Valid(true)
        }
      }

      """return blocking.withdrawals = false when accountBlockingCode or clientBlockingCode is "00" or "12" or "15""" in {
        testBlockingCodes("00", "12", "15") {
          _.map(_.blocked.withdrawals) shouldBe Valid(false)
        }
      }

      """return blocking.bonuses = true when accountBlockingCode or clientBlockingCode is not "00" or "12""" in {
        testBlockingCodes("11", "13", "15", "30", "64") {
          _.map(_.blocked.bonuses) shouldBe Valid(true)
        }
      }

      """return blocking.bonuses = false when accountBlockingCode or clientBlockingCode is "00" or "12""" in {
        testBlockingCodes("00", "12") {
          _.map(_.blocked.bonuses) shouldBe Valid(false)
        }
      }

      "return an error for unknown blocking codes" in {
        testBlockingCodes("61", "abc")(_.isInvalid shouldBe true)
      }

      "return an error for unknown accountClosedFlag values" in {
        val returnedAccount =
          Account(testNsiAccount.copy(accountClosedFlag = "O", accountBalance = BigDecimal("123.45")))
        returnedAccount.isInvalid shouldBe true
      }

      "return account details for closed account" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            accountClosedFlag = "C",
            accountBalance = BigDecimal("0.00"),
            accountClosureDate = Some(LocalDate.of(2018, 2, 16)),
            accountClosingBalance = Some(BigDecimal("123.45"))
          )
        )

        returnedAccount shouldBe Valid(
          account.copy(
            isClosed = true,
            balance = 0,
            closureDate = Some(LocalDate.of(2018, 2, 16)),
            closingBalance = Some(123.45)
          )
        )
      }

      "return details for current month" in {

        val returnedAccount = Account(
          testNsiAccount.copy(
            currentInvestmentMonth = NsiCurrentInvestmentMonth(
              investmentRemaining = BigDecimal("12.34"),
              investmentLimit = 50,
              endDate = LocalDate.of(2019, 6, 23)
            )
          )
        )

        returnedAccount shouldBe Valid(
          account.copy(
            balance = 0,
            paidInThisMonth = 37.66,
            canPayInThisMonth = 12.34,
            maximumPaidInThisMonth = 50,
            thisMonthEndDate = LocalDate.of(2019, 6, 23)
          )
        )
      }

      "return None when the payment amounts for current month don't make sense because investmentRemaining > investmentLimit" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            currentInvestmentMonth = testNsiAccount.currentInvestmentMonth
              .copy(investmentRemaining = BigDecimal("50.01"), investmentLimit = 50)
          )
        )

        returnedAccount.isInvalid shouldBe true
      }

      "return payment amounts for current month when investmentRemaining == investmentLimit (boundary case for previous test)" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            currentInvestmentMonth =
              testNsiAccount.currentInvestmentMonth.copy(investmentRemaining = 50, investmentLimit = 50)
          )
        )
        returnedAccount shouldBe Valid(
          account.copy(balance = 0, paidInThisMonth = 0, canPayInThisMonth = 50, maximumPaidInThisMonth = 50)
        )
      }

      "return bonus information including calculated bonusPaidOnOrAfterDate" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            terms = Seq(
              NsiBonusTerm(
                termNumber = 1,
                startDate = LocalDate.of(2018, 10, 22),
                endDate = LocalDate.of(2020, 10, 22),
                bonusEstimate = BigDecimal("65.43"),
                bonusPaid = 0
              )
            )
          )
        )

        val bonusTerm = BonusTerm(
          bonusEstimate = BigDecimal("65.43"),
          bonusPaid = 0,
          startDate = LocalDate.of(2018, 10, 22),
          endDate = LocalDate.of(2020, 10, 22),
          bonusPaidOnOrAfterDate = LocalDate.of(2020, 10, 23)
        )
        returnedAccount shouldBe Valid(
          account.copy(
            openedYearMonth = YearMonth.of(2018, 10),
            balance = 0,
            bonusTerms = List(bonusTerm)
          )
        )
      }

      "sort the bonus terms by termNumber" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            accountBalance = BigDecimal("200.34"),
            terms = Seq(
              NsiBonusTerm(
                termNumber = 2,
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2021, 12, 31),
                bonusEstimate = 67,
                bonusPaid = 0
              ),
              NsiBonusTerm(
                termNumber = 1,
                startDate = LocalDate.of(2018, 1, 1),
                endDate = LocalDate.of(2019, 12, 31),
                bonusEstimate = BigDecimal("123.45"),
                bonusPaid = BigDecimal("123.45")
              )
            )
          )
        )

        val bonusTerm1 = BonusTerm(
          bonusEstimate = BigDecimal("67"),
          bonusPaid = 0,
          startDate = LocalDate.of(2020, 1, 1),
          endDate = LocalDate.of(2021, 12, 31),
          bonusPaidOnOrAfterDate = LocalDate.of(2022, 1, 1)
        )
        val bonusTerm2 = BonusTerm(
          bonusEstimate = BigDecimal("123.45"),
          bonusPaid = 123.45,
          startDate = LocalDate.of(2018, 1, 1),
          endDate = LocalDate.of(2019, 12, 31),
          bonusPaidOnOrAfterDate = LocalDate.of(2020, 1, 1)
        )
        returnedAccount shouldBe Valid(
          account.copy(
            openedYearMonth = YearMonth.of(2018, 1),
            balance = 200.34,
            bonusTerms = List(bonusTerm2, bonusTerm1)
          )
        )
      }

      "calculate openedYearMonth based on start of first bonus term" in {
        val returnedAccount = Account(
          testNsiAccount.copy(
            terms = Seq(
              NsiBonusTerm(
                termNumber = 2,
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2021, 12, 31),
                bonusEstimate = 67,
                bonusPaid = 0
              ),
              NsiBonusTerm(
                termNumber = 1,
                startDate = LocalDate.of(2018, 1, 1),
                endDate = LocalDate.of(2019, 12, 31),
                bonusEstimate = BigDecimal("123.45"),
                bonusPaid = BigDecimal("123.45")
              )
            )
          )
        )

        returnedAccount.bimap(
          errors => fail(s"returnedAccount should have been Valid but was Invalid with errors $errors"),
          account => account.openedYearMonth shouldBe YearMonth.of(2018, 1)
        )
      }

      // NS&I should always return 2 terms, and we need at least the first one to calculate openedYearMonth
      "return an Invalid when the NS&I account does not contain any bonus terms" in {
        val returnedAccount = Account(testNsiAccount.copy(terms = Seq.empty))

        returnedAccount shouldBe Invalid(NonEmptyList.of("Bonus terms list returned by NS&I was empty"))
      }
    }
  }

}
