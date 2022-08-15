/*
 * Copyright 2022 HM Revenue & Customs
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
import com.mongodb.client.model.Indexes._
import com.mongodb.client.model.{Accumulators, Aggregates, Projections}
import org.mongodb.scala.bson.{BsonDocument, BsonValue}
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.repo.MongoEligibilityStatsStore._
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEligibilityStatsStore])
trait EligibilityStatsStore {

  def getEligibilityStats: Future[List[EligibilityStats]]

}

@Singleton
class MongoEligibilityStatsStore @Inject() (mongo:   MongoComponent,
                                            metrics: Metrics)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[EnrolmentData](
    mongoComponent = mongo,
    collectionName = "enrolments",
    domainFormat   = EnrolmentData.ninoFormat,
    indexes        = Seq(IndexModel(ascending("eligibilityReason"), IndexOptions().name("eligibilityReasonIndex").unique(false)))
  ) with EligibilityStatsStore with Logging {

  private[repo] def doAggregate(): Future[List[EligibilityStats]] = {
    println("Doing aggregate")
    import MongoEligibilityStatsStore.format

    collection.aggregate[BsonValue](Seq(
      Aggregates.group(BsonDocument("eligibilityReason" -> "$eligibilityReason", "source" -> "$source"), Accumulators.sum("total", 1)),
      Aggregates.project(Projections.fields(
        Projections.computed("_id", 0),
        Projections.computed("eligibilityReason", "$_id.eligibilityReason"),
        Projections.computed("source", "$_id.source"),
        Projections.computed("total", "$total"),
      ))
    )).toFuture()
      .map(_.toList.map(Codecs.fromBson[EligibilityStats]))

  }

  override def getEligibilityStats: Future[List[EligibilityStats]] = {
    println("Getting Elig stats")
    val timerContext = metrics.eligibilityStatsTimer.time()
    doAggregate()
      .map { response ⇒
        println("Got  Elig stats")
        val time = timerContext.stop()
        logger.info(s"eligibility stats query took ${nanosToPrettyString(time)}")
        response
      }.recover {
        case e ⇒
          println("We failed")
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

