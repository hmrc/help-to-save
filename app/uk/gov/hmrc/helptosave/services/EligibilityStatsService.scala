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

package uk.gov.hmrc.helptosave.services

import cats.instances.int._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EligibilityStatsServiceImpl])
trait EligibilityStatsService {

  def getEligibilityStats()(implicit ec: ExecutionContext): Future[Either[String, String]]

}

@Singleton
class EligibilityStatsServiceImpl @Inject() (eligibilityReportStore: EligibilityStatsStore,
                                             metrics:                Metrics)(implicit ninoLogMessageTransformer: LogMessageTransformer)
  extends EligibilityStatsService with Logging {

  override def getEligibilityStats()(implicit ec: ExecutionContext): Future[Either[String, String]] = {
    val timerContext = metrics.eligibilityStatsTimer.time()
    eligibilityReportStore.getEligibilityStats()
      .map { report ⇒
        val _ = timerContext.stop()

          def get(reason: Option[Int]): (Int, Int, Int, Int) = {
              def count(source: Option[String]) =
                report.filter(p ⇒ p.eligibilityReason === reason && p.source === source) match {
                  case head :: Nil ⇒ head.total
                  case _           ⇒ 0
                }

            (count(Some("Digital")), count(Some("Stride")), count(Some("KCOM")), count(None))
          }

          def totals() = {
              def count(reason: Option[Int]) = report.filter(p ⇒ p.eligibilityReason === reason).map(_.total).sum

            (count(Some(6)), count(Some(7)), count(Some(8)), count(None))
          }

        val (ucDigitalStats, ucStrideStats, ucKcomStats, ucUnknownStats) = get(Some(6))
        val (wtcDigitalStats, wtcStrideStats, wtcKcomStats, wtcUnknownStats) = get(Some(7))
        val (ucAndWtcDigitalStats, ucAndWtcStrideStats, ucAndWtcKcomStats, ucAndWtcUnknownStats) = get(Some(8))
        val (unKnownDigitalStats, unKnownStrideStats, unknownKcomStats, unKnownUnknownStats) = get(None)
        val (ucTotal, wtcTotal, ucAndWtcTotal, unKnownTotal) = totals()

          def channelTotals() = {
              def count(source: Option[String]) =
                report.filter(p ⇒ p.source === source).map(_.total).sum

            (count(Some("Digital")), count(Some("Stride")), count(Some("KCOM")), count(None))
          }

        val (digitalTotal, strideTotal, kcomTotal, unknownTotal) = channelTotals()

        val result = Tabulator.format(List(
          List("Reason", "Channel", "Count"),
          List("UC", "Digital", s"$ucDigitalStats"),
          List("", "Stride", s"$ucStrideStats"),
          List("", "KCOM", s"$ucKcomStats"),
          List("", "Unknown", s"$ucUnknownStats"),
          List("", "Total", s"$ucTotal"),
          List(" ", " ", " "),
          List("WTC", "Digital", s"$wtcDigitalStats"),
          List("", "Stride", s"$wtcStrideStats"),
          List("", "KCOM", s"$wtcKcomStats"),
          List("", "Unknown", s"$wtcUnknownStats"),
          List("", "Total", s"$wtcTotal"),
          List(" ", " ", " "),
          List("UC & WTC", "Digital", s"$ucAndWtcDigitalStats"),
          List("", "Stride", s"$ucAndWtcStrideStats"),
          List("", "KCOM", s"$ucAndWtcKcomStats"),
          List("", "Unknown", s"$ucAndWtcUnknownStats"),
          List("", "Total", s"$ucAndWtcTotal"),
          List(" ", " ", " "),
          List("Unkown", "Digital", s"$unKnownDigitalStats"),
          List("", "Stride", s"$unKnownStrideStats"),
          List("", "KCOM", s"$unknownKcomStats"),
          List("", "Unknown", s"$unKnownUnknownStats"),
          List("", "Total", s"$unKnownTotal"),
          List(" ", " ", " "),
          List("Totals", "Digital", s"$digitalTotal"),
          List("", "Stride", s"$strideTotal"),
          List("", "KCOM", s"$kcomTotal"),
          List("", "Unknown", s"$unknownTotal"),
          List("", "Total", s"${report.map(_.total).sum}")
        ))

        Right(result)
      }.recover {
        case e ⇒
          val _ = timerContext.stop()
          Left(e.getMessage)
      }
  }
}

object Tabulator {
  def format(table: Seq[Seq[String]]): String = table match {
    case x: Seq[Seq[String]] if x.isEmpty ⇒ ""
    case _ ⇒
      val sizes: Seq[Seq[Int]] = for (row ← table) yield for (cell ← row)
        yield cell.length
      val colSizes = for (col ← sizes.transpose) yield col.max
      val rows = for (row ← table) yield formatRow(row, colSizes)
      formatRows(rowSeparator(colSizes), rows)
  }

  private def formatRows(rowSeparator: String, rows: Seq[String]): String =

    "\n" + (
      rowSeparator ::
      rows.headOption.getOrElse("") ::
      rowSeparator ::
      rows.drop(1).toList :::
      rowSeparator ::
      List()).mkString("\n")

  private def formatRow(row: Seq[String], colSizes: Seq[Int]) = {
    val cells = for ((item, size) ← row.zip(colSizes)) yield if (size === 0) "" else s"%${size}s".format(item)
    cells.mkString("|", "|", "|")
  }

  private def rowSeparator(colSizes: Seq[Int]) = colSizes map {
    "-" * _
  } mkString ("+", "+", "+")
}
