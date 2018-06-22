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

package uk.gov.hmrc.helptosave.actors

import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.util.Logging

@ImplementedBy(classOf[EligibilityStatsHandlerImpl])
trait EligibilityStatsHandler {
  def handleStats(result: List[EligibilityStats]): String
}

@Singleton
class EligibilityStatsHandlerImpl extends EligibilityStatsHandler with Logging {

  type Table = List[(String, List[(String, Int)])]

  private val emptyTable = List.empty[(String, List[(String, Int)])]

  override def handleStats(stats: List[EligibilityStats]): String = {

    val report = prettyFormatTable(createTable(stats))

    logger.info(s"report is $report")
    //for unit testing
    report
  }

  def createTable(stats: List[EligibilityStats]): Table = {

    val groupedByEligibilityReason: Map[Option[Int], List[EligibilityStats]] = stats.groupBy(_.eligibilityReason)

    val groupedByEligibilityReasonThenSource: Table =
      groupedByEligibilityReason.map {
        case (k, v) ⇒
          k.map(_.toString).getOrElse("Unknown") -> {
            v.groupBy(_.source).map(x ⇒ x._1.getOrElse("Unknown") → x._2.map(_.total).sum).toList.sortBy(_._1)
          }
      }.toList.sortBy(_._1)

    val tableWithMissingSources = addMissingSourceTotals(groupedByEligibilityReasonThenSource)

    mapIntToReasonString(tableWithMissingSources)
  }

  private def addMissingSourceTotals(table: Table) = {
    //add missing sources with total 0 for each reason
    val sources = allSourcesFromMongo(table)
    table.foldLeft[Table](emptyTable) {
      case (a, b) ⇒

        val x = sources.foldLeft[List[(String, Int)]](List.empty[(String, Int)]) {
          case (c, d) ⇒
            if (!b._2.map(_._1).contains(d)) {
              (d, 0) :: c
            } else {
              c
            }
        }
        (b._1, x ::: b._2) :: a
    }
  }

  private def mapIntToReasonString(table: Table) = {
    table
      .foldLeft[Table](emptyTable) {
        case (modified, original) ⇒
          if (original._1 === "6") {
            val t: (String, List[(String, Int)]) = ("UC", original._2)
            t :: modified
          } else if (original._1 === "7") {
            val t: (String, List[(String, Int)]) = ("WTC", original._2)
            t :: modified
          } else if (original._1 === "8") {
            val t: (String, List[(String, Int)]) = ("UC&WTC", original._2)
            t :: modified
          } else {
            val t: (String, List[(String, Int)]) = original
            t :: modified
          }
      }
  }

  def prettyFormatTable(table: Table): String = {
    val f = "|%8s|%10s|%10s|\n"

    val header: String =
      s"""
         |+--------+----------+----------+
         || Reason | Channel  |  Count   |
         |+--------+----------+----------+\n""".stripMargin

    var report = table.foldLeft[String](header) {
      case (row: String, element: (String, List[(String, Int)])) ⇒
        val a = element._2.foldLeft[String](row) {
          case (x: String, y: (String, Int)) ⇒
            x.concat(f.format(element._1, y._1, y._2))
        }

          def getTotalByReason =
            table
              .filter(p ⇒ p._1 === element._1)
              .flatMap(_._2)
              .map(_._2)
              .sum

        a.concat(f.format("", "Total", getTotalByReason))
          .concat(f.format("", "", ""))

    }

    //compute totals by source
    allSourcesFromMongo(table).foreach {
      p ⇒
        val t = table.map(_._2).map(_.filter(_._1 === p).map(_._2)).map(_.sum).sum
        report = report.concat(f.format("Total", p, t))
    }

    //add grand total
    report
      .concat(f.format("", "Total", table.flatMap(_._2).map(_._2).sum))
      .concat("+--------+----------+----------+")
  }

  private def allSourcesFromMongo(table: Table): Set[String] =
    table.foldLeft[Set[String]](Set.empty[String]) {
      case (a, b) ⇒ b._2.foldLeft[Set[String]](a) {
        case (c, d) ⇒ c.+(d._1)
      }
    }
}
