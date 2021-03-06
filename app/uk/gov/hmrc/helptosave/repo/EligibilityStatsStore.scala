/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.repo

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.commands.JSONAggregationFramework._
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore._
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEligibilityStatsStore])
trait EligibilityStatsStore {

  def getEligibilityStats: Future[List[EligibilityStats]]

}

@Singleton
class MongoEligibilityStatsStore @Inject() (mongo:   ReactiveMongoComponent,
                                            metrics: Metrics)(implicit ec: ExecutionContext)
  extends ReactiveRepository[EnrolmentData, BSONObjectID](
    collectionName = "enrolments",
    mongo          = mongo.mongoConnector.db,
    EnrolmentData.ninoFormat,
    ReactiveMongoFormats.objectIdFormats) with EligibilityStatsStore {

  val log: Logger = new Logger(logger)

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("eligibilityReason" → IndexType.Ascending),
      name = Some("eligibilityReasonIndex")
    )
  )

  private[repo] def doAggregate(): Future[List[EligibilityStats]] = {
    collection.aggregateWith(){ a ⇒
      a.Group(Json.obj("eligibilityReason" -> "$eligibilityReason", "source" -> "$source"))("total" -> a.SumAll) →
        List(Project(Json.obj("_id" -> 0, "eligibilityReason" -> "$_id.eligibilityReason", "source" -> "$_id.source", "total" -> "$total")))
    }.fold(Nil: List[EligibilityStats])((acc, cur) ⇒ cur :: acc)
  }

  override def getEligibilityStats: Future[List[EligibilityStats]] = {
    val timerContext = metrics.eligibilityStatsTimer.time()
    doAggregate()
      .map { response ⇒
        val time = timerContext.stop()
        logger.info(s"eligibility stats query took ${nanosToPrettyString(time)}")
        response
      }.recover {
        case e ⇒
          val _ = timerContext.stop()
          logger.warn(s"error retrieving the eligibility stats from mongo, error = ${e.getMessage}")
          List.empty[EligibilityStats]
      }
  }
}

object MongoEligibilityStatsStore {

  case class EligibilityStats(eligibilityReason: Option[Int], source: Option[String], total: Int)

  implicit val format: Format[EligibilityStats] = Json.format[EligibilityStats]
}

