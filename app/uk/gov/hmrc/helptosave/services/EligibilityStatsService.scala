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

          def channelTotals(): (Int, Int, Int, Int) = {
              def count(source: Option[String]) =
                report.filter(p ⇒ p.source === source).map(_.total).sum

            (count(Some("Digital")), count(Some("Stride")), count(Some("KCOM")), count(None))
          }

        val (digitalTotal, strideTotal, kcomTotal, unKnownOverallTotal) = channelTotals()

        val overallTotal = report.map(_.total).sum

        val result =
          s"""
          |+--------+-------+-----+
          ||  Reason|Channel|Count|
          |+--------+-------+-----+
          ||      UC|Digital|    $ucDigitalStats|
          ||        | Stride|    $ucStrideStats|
          ||        |   KCOM|    $ucKcomStats|
          ||        |Unknown|    $ucUnknownStats|
          ||        |  Total|    $ucTotal|
          ||        |       |     |
          ||     WTC|Digital|    $wtcDigitalStats|
          ||        | Stride|    $wtcStrideStats|
          ||        |   KCOM|    $wtcKcomStats|
          ||        |Unknown|    $wtcUnknownStats|
          ||        |  Total|    $wtcTotal|
          ||        |       |     |
          ||UC & WTC|Digital|    $ucAndWtcDigitalStats|
          ||        | Stride|    $ucAndWtcStrideStats|
          ||        |   KCOM|    $ucAndWtcKcomStats|
          ||        |Unknown|    $ucAndWtcUnknownStats|
          ||        |  Total|    $ucAndWtcTotal|
          ||        |       |     |
          || Unknown|Digital|    $unKnownDigitalStats|
          ||        | Stride|    $unKnownStrideStats|
          ||        |   KCOM|    $unknownKcomStats|
          ||        |Unknown|    $unKnownUnknownStats|
          ||        |  Total|    $unKnownTotal|
          ||        |       |     |
          ||  Totals|Digital|    $digitalTotal|
          ||        | Stride|    $strideTotal|
          ||        |   KCOM|    $kcomTotal|
          ||        |Unknown|    $unKnownOverallTotal|
          ||        |  Total|    $overallTotal|
          |+--------+-------+-----+
          """.stripMargin

        Right(result)
      }.recover {
        case e ⇒
          val _ = timerContext.stop()
          Left(e.getMessage)
      }
  }
}
