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

import cats.Eq
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.eq._
import cats.syntax.traverse._
import play.api.libs.json.{JsString, JsValue, Json, Writes}

sealed trait Operation
case object Credit extends Operation
case object Debit extends Operation

object Operation {
  implicit val eqOperation: Eq[Operation] = Eq.fromUniversalEquals
  implicit val writes: Writes[Operation] = new Writes[Operation] {
    override def writes(o: Operation): JsValue = JsString(o match {
      case Credit ⇒ "credit"
      case Debit  ⇒ "debit"
    })
  }
}

private case class ValidNsiTransaction(
    sequence:             Int,
    operation:            Operation,
    amount:               BigDecimal,
    transactionDate:      LocalDate,
    accountingDate:       LocalDate,
    description:          String,
    transactionReference: String
)

private object ValidNsiTransaction {
  private type ValidOrErrorString[A] = ValidatedNel[String, A]

  def apply(nsiTransaction: NsiTransaction): ValidatedNel[String, ValidNsiTransaction] = {
      def operationValidation(nsiOperation: String): ValidOrErrorString[Operation] =
        if (nsiOperation === "C") { Valid(Credit) }
        else if (nsiOperation === "D") { Valid(Debit) }
        else { Invalid(NonEmptyList.one(s"""Unknown value for operation: "$nsiOperation"""")) }

      def sequenceValidation(nsiSequence: String): ValidOrErrorString[Int] =
        try Valid(nsiSequence.toInt)
        catch {
          case _: NumberFormatException ⇒ Invalid(NonEmptyList.one(s"""Can't parse sequence value as an integer: "$nsiSequence""""))
        }

    (operationValidation(nsiTransaction.operation), sequenceValidation(nsiTransaction.sequence)).mapN {
      case (operation, sequence) ⇒
        ValidNsiTransaction(
          sequence,
          operation,
          nsiTransaction.amount,
          nsiTransaction.transactionDate,
          nsiTransaction.accountingDate,
          nsiTransaction.description,
          nsiTransaction.transactionReference
        )
    }
  }
}

case class Transaction(
    operation:            Operation,
    amount:               BigDecimal,
    transactionDate:      LocalDate,
    accountingDate:       LocalDate,
    description:          String,
    transactionReference: String,
    balanceAfter:         BigDecimal
)

object Transaction {
  implicit val writes: Writes[Transaction] = Json.writes[Transaction]

  def apply(vnt: ValidNsiTransaction, balanceAfter: BigDecimal): Transaction = Transaction(
    vnt.operation,
    vnt.amount,
    vnt.transactionDate,
    vnt.accountingDate,
    vnt.description,
    vnt.transactionReference,
    balanceAfter
  )
}

case class Transactions(transactions: Seq[Transaction])

object Transactions {
  implicit val writes: Writes[Transactions] = Json.writes[Transactions]

  private type ValidOrErrorString[A] = ValidatedNel[String, A]

  def apply(nsiTransactions: NsiTransactions): ValidatedNel[String, Transactions] = {
    nsiTransactions.transactions.toList
      .traverse[ValidOrErrorString, ValidNsiTransaction](ValidNsiTransaction.apply)
      .map(sortLikeNsiWeb _ andThen runningBalance andThen Transactions.apply)
  }

  private implicit val eqLocalDate: Eq[LocalDate] = Eq.instance(_.isEqual(_))

  private def sortLikeNsiWeb(transactions: Seq[ValidNsiTransaction]): Seq[ValidNsiTransaction] = {
    transactions.sortWith { (t1, t2) ⇒ t1.sequence < t2.sequence }
  }

  private def runningBalance(transactions: Seq[ValidNsiTransaction]): Seq[Transaction] = {
    transactions.foldLeft((Vector.empty[Transaction], 0: BigDecimal)){ (acc, vnt: ValidNsiTransaction) ⇒
      val (accTransactions, accBalance) = acc

      val newBalance = if (vnt.operation === Credit) {
        accBalance + vnt.amount
      } else {
        accBalance - vnt.amount
      }

      val newT = Transaction(vnt, newBalance)

      (accTransactions :+ newT, newBalance)
    }._1
  }
}
