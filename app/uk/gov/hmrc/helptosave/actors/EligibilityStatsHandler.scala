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

    val sourcesFromMongo = stats.map(_.source.getOrElse("Unknown")).toSet

    val report = prettyFormatTable(createTable(stats, sourcesFromMongo), sourcesFromMongo)

    logger.info(s"report is $report")
    //for unit testing
    report
  }

  def createTable(stats: List[EligibilityStats], allSources: Set[String]): Table = {

    val groupedByEligibilityReason: Map[Option[Int], List[EligibilityStats]] = stats.groupBy(_.eligibilityReason)

    val groupedByEligibilityReasonThenSource: Table =
      groupedByEligibilityReason.map {
        case (k, v) ⇒
          k.map(_.toString).getOrElse("Unknown") -> {
            v.groupBy(_.source).map(x ⇒ x._1.getOrElse("Unknown") → x._2.map(_.total).sum).toList
          }
      }.toList

    addMissingSourceTotals(groupedByEligibilityReasonThenSource, allSources)
  }

  private def addMissingSourceTotals(table: Table, allSources: Set[String]) = {
    //add missing sources with total 0 for each reason
    table.foldLeft[Table](emptyTable) {
      case (acc, curr) ⇒
        val missing: List[(String, Int)] = allSources.toList.diff(curr._2.map(_._1)).map(_ → 0)
        (curr._1, (curr._2 ::: missing).sortBy(_._1)) :: acc
    }.sortBy(_._1)
  }

  def prettyFormatTable(table: Table, allSources: Set[String]): String = {
    val f = "|%8s|%10s|%10s|\n"

    val header: String =
      s"""
         |+--------+----------+----------+
         || Reason | Channel  |  Count   |
         |+--------+----------+----------+\n""".stripMargin

      def getStringFrom(reason: String) = {
        if (reason === "6") {
          "UC"
        } else if (reason === "7") {
          "WTC"
        } else if (reason === "8") {
          "UC&WTC"
        } else {
          reason
        }
      }

    var report = table.foldLeft[String](header) {
      case (acc, curr) ⇒
        val r = curr._2.foldLeft[String]("") {
          case (acc1, curr1) ⇒
            acc1.concat(f.format(getStringFrom(curr._1), curr1._1, curr1._2))

        }
        acc
          .concat(r)
          .concat(f.format("", "Total", curr._2.map(_._2).sum))
          .concat(f.format("", "", ""))
    }

    //compute totals by source
    allSources.toList.sorted(Ordering.String).foreach {
      p ⇒
        val total = table.flatMap(_._2.filter(_._1 === p).map(_._2)).sum
        report = report.concat(f.format("Total", p, total))
    }

    //add grand total
    report
      .concat(f.format("", "Total", table.flatMap(_._2.map(_._2)).sum))
      .concat("+--------+----------+----------+")
  }
}
