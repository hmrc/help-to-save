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

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import uk.gov.hmrc.helptosave.utils.TestSupport

import java.time.LocalDate

class TransactionsSpec extends TestSupport {

  private val nsiCreditTransaction      = NsiTransaction(
    sequence = "1",
    amount = BigDecimal("1.23"),
    operation = "C",
    description = "description",
    transactionReference = "reference",
    transactionDate = LocalDate.parse("1900-01-01"),
    accountingDate = LocalDate.parse("1900-01-02")
  )
  private val validNsiCreditTransaction = ValidNsiTransaction(
    1,
    Credit,
    BigDecimal("1.23"),
    LocalDate.parse("1900-01-01"),
    LocalDate.parse("1900-01-02"),
    "description",
    "reference"
  )
  private val creditTransaction         = Transaction(
    Credit,
    BigDecimal("1.23"),
    LocalDate.parse("1900-01-01"),
    LocalDate.parse("1900-01-02"),
    "description",
    "reference",
    BigDecimal(0)
  )

  private val nsiDebitTransaction: NsiTransaction           = nsiCreditTransaction.copy(operation = "D")
  private val validNsiDebitTransaction: ValidNsiTransaction = validNsiCreditTransaction.copy(operation = Debit)
  private val debitTransaction: Transaction                 = creditTransaction.copy(operation = Debit)

  private val nsiInvalidOperationTransaction: NsiTransaction = nsiCreditTransaction.copy(operation = "X")

  private val nsiInvalidOperationTransaction2: NsiTransaction = nsiCreditTransaction.copy(operation = "")

  "ValidNsiTransaction object" when {
    "creating a new Transaction from a NsiTransaction" must {
      "return transaction details for a credit" in {
        ValidNsiTransaction(nsiCreditTransaction) shouldBe Valid(validNsiCreditTransaction)
      }

      "return transaction details for a debit" in {
        ValidNsiTransaction(nsiDebitTransaction) shouldBe Valid(validNsiDebitTransaction)
      }

      "return an Invalid for an unknown operation" in {
        ValidNsiTransaction(nsiInvalidOperationTransaction) shouldBe Invalid(
          NonEmptyList.one(
            """Unknown value for operation: "X""""
          )
        )
      }

      "return an Invalid for a sequence that cannot be parsed as an integer" in {
        ValidNsiTransaction(nsiCreditTransaction.copy(sequence = "one")) shouldBe Invalid(
          NonEmptyList.one(
            """Can't parse sequence value as an integer: "one""""
          )
        )
        ValidNsiTransaction(nsiCreditTransaction.copy(sequence = "1.1")) shouldBe Invalid(
          NonEmptyList.one(
            """Can't parse sequence value as an integer: "1.1""""
          )
        )
      }
    }
  }

  "Transactions object" when { // scalastyle:off magic.number
    "creating a new Transactions from a NsiTransactions" must {
      "return transactions details when input transactions are all valid" in {
        val nsiTransactions      = NsiTransactions(
          Seq(nsiCreditTransaction, nsiCreditTransaction.copy(sequence = "2"), nsiDebitTransaction.copy(sequence = "3"))
        )
        val expectedTransactions = Transactions(
          Seq(
            creditTransaction.copy(balanceAfter = BigDecimal("1.23")),
            creditTransaction.copy(balanceAfter = BigDecimal("2.46")),
            debitTransaction.copy(balanceAfter = BigDecimal("1.23"))
          )
        )
        Transactions(nsiTransactions) shouldBe Valid(expectedTransactions)
      }

      "handle an empty transaction list" in {
        val nsiTransactions      = NsiTransactions(Seq())
        val expectedTransactions = Transactions(Seq())
        Transactions(nsiTransactions) shouldBe Valid(expectedTransactions)
      }

      "handle a 1-element transaction list" in {
        val nsiTransactions      = NsiTransactions(Seq(nsiDebitTransaction))
        val expectedTransactions =
          Transactions(Seq(debitTransaction.copy(balanceAfter = 0 - validNsiDebitTransaction.amount)))
        Transactions(nsiTransactions) shouldBe Valid(expectedTransactions)
      }

      "sort transactions by sequence number" in {
        val nsiTransactions = NsiTransactions(
          Seq(
            nsiCreditTransaction
              .copy(sequence = "5", accountingDate = LocalDate.parse("2018-04-10"), amount = BigDecimal(5)),
            nsiCreditTransaction
              .copy(sequence = "3", accountingDate = LocalDate.parse("2017-11-27"), amount = BigDecimal(3)),
            nsiCreditTransaction
              .copy(sequence = "4", accountingDate = LocalDate.parse("2017-11-27"), amount = BigDecimal(4)),
            nsiDebitTransaction
              .copy(sequence = "2", accountingDate = LocalDate.parse("2017-11-27"), amount = BigDecimal(2)),
            nsiCreditTransaction
              .copy(sequence = "1", accountingDate = LocalDate.parse("2017-11-20"), amount = BigDecimal(1))
          )
        )

        Transactions(nsiTransactions).bimap(
          errors => fail(errors.toList.mkString(", ")),
          transactions =>
            transactions.transactions.map(_.amount) shouldBe List(
              BigDecimal(1),
              BigDecimal(2),
              BigDecimal(3),
              BigDecimal(4),
              BigDecimal(5)
            )
        )
      }

      "return an Invalid when any input transactions are invalid" in {
        val nsiTransactions =
          NsiTransactions(Seq(nsiCreditTransaction, nsiInvalidOperationTransaction, nsiInvalidOperationTransaction2))
        Transactions(nsiTransactions) shouldBe Invalid(
          NonEmptyList.of(
            """Unknown value for operation: "X"""",
            """Unknown value for operation: """""
          )
        )
      }
    }
  }
}
