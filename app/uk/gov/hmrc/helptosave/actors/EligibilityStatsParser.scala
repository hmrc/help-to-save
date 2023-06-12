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

package uk.gov.hmrc.helptosave.actors

import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.helptosave.actors.EligibilityStatsParser._
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore.EligibilityStats
import uk.gov.hmrc.helptosave.util.Logging

@ImplementedBy(classOf[EligibilityStatsParserImpl])
trait EligibilityStatsParser {
  def createTable(result: List[EligibilityStats]): Table

  def prettyFormatTable(table: Table): String

}

object EligibilityStatsParser {

  type EligibilityReason = String
  type Source = String

  type Table = Map[EligibilityReason, Map[Source, Int]]
}

@Singleton
class EligibilityStatsParserImpl extends EligibilityStatsParser with Logging {

  private val emptyTable = Map.empty[EligibilityReason, Map[Source, Int]]

  override def createTable(stats: List[EligibilityStats]): Table = {
    val sourcesFromMongo = stats.map(_.source.getOrElse("Unknown")).toSet
    createTable(stats, sourcesFromMongo)
  }

  private def createTable(stats: List[EligibilityStats], allSources: Set[Source]): Table = {
    val groupedByEligibilityReason: Map[Option[Int], List[EligibilityStats]] = stats.groupBy(_.eligibilityReason)

    val groupedByEligibilityReasonThenSource: Table =
      groupedByEligibilityReason.map {
        case (k, v) =>
          k.map(_.toString).getOrElse("Unknown") -> {
            v.groupBy(_.source).map(x => x._1.getOrElse("Unknown") -> x._2.map(_.total).sum)
          }
      }

    addMissingSourceTotals(groupedByEligibilityReasonThenSource, allSources)
  }

  private def addMissingSourceTotals(table: Table, allSources: Set[Source]) = {
    //add missing sources with total 0 for each reason
    table.foldLeft[Table](emptyTable) {
      case (acc, curr) =>
        val missing: List[(String, Int)] = allSources.toList.diff(curr._2.keys.toList).map(_ -> 0)
        acc.updated(curr._1, curr._2 ++ missing)
    }
  }

  override def prettyFormatTable(table: Table): String = {
    val allSourcesSorted: List[Source] = table.values.toList.flatMap(_.keys.toList).distinct.sorted

    val (sourceColumnWidth, reasonColumnWidth, totalColumnWidth) =
      (allSourcesSorted.map(_.length).max + 1,
        table.keys.map(_.length).max + 1,
        10)

    val rowFormat: String = s"|%${reasonColumnWidth}s|%${sourceColumnWidth}s|%${totalColumnWidth}s|\n"
    val rowSeparator = s"+${"-" * reasonColumnWidth}+${"-" * sourceColumnWidth}+${"-" * totalColumnWidth}+"
    val tableHeader: String = s"$rowSeparator\n${rowFormat.format("Reason", "Channel", "Count")}$rowSeparator\n"

    val sortedTable: List[(EligibilityReason, List[(Source, Int)])] =
      table.toList.sortBy(_._1).map{ case (k, v) => k -> v.toList.sortBy(_._1) }

    val report = sortedTable.foldLeft[String](tableHeader) {
      case (acc, (reason, sources)) =>
        val rows = sources.foldLeft[(String, Boolean)]("" -> true) {
          case ((acc1, displayReason), (source, count)) =>
            acc1.concat(rowFormat.format(if (displayReason) { getStringFrom(reason) } else { "" }, source, count)) -> false
        }._1
        acc
          .concat(rows)
          .concat(rowFormat.format("", "Total", sources.map(_._2).sum))
          .concat(rowFormat.format("", "", ""))
    }

    //compute totals by source
    val sourcesTotalsRows: String = allSourcesSorted.foldLeft("" -> true){
      case ((acc, displayReason), source) =>
        val total = sortedTable.flatMap(_._2.filter(_._1 === source).map(_._2)).sum
        acc.concat(rowFormat.format(if (displayReason) { "Total" } else { "" }, source, total)) -> false
    }._1

    //add grand total
    report
      .concat(sourcesTotalsRows)
      .concat(rowFormat.format("", "Total", sortedTable.flatMap(_._2.map(_._2)).sum))
      .concat(rowSeparator)
  }

  private def getStringFrom(reason: String) = {
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
}
