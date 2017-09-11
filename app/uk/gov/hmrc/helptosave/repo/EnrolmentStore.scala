/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEnrolmentStore])
trait EnrolmentStore {
  import EnrolmentStore._

  def get(nino: NINO): EitherT[Future, String, Status]

  def update(nino: NINO, itmpFlag: Boolean): EitherT[Future, String, Unit]

}

object EnrolmentStore {

  sealed trait Status

  case class Enrolled(itmpHtSFlag: Boolean) extends Status

  case object NotEnrolled extends Status

}

class MongoEnrolmentStore @Inject() (mongo:   ReactiveMongoComponent,
                                     metrics: Metrics)(implicit ec: ExecutionContext)
  extends ReactiveRepository[EnrolmentData, BSONObjectID](
    collectionName = "enrolments",
    mongo          = mongo.mongoConnector.db,
    EnrolmentData.ninoFormat,
    ReactiveMongoFormats.objectIdFormats)
  with EnrolmentStore {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  private[repo] def doUpdate(nino: NINO, itmpFlag: Boolean)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
    collection.findAndUpdate(
      BSONDocument("nino" -> nino),
      BSONDocument("$set" -> BSONDocument("itmpHtSFlag" -> itmpFlag)),
      fetchNewObject = true,
      upsert         = true
    ).map(_.result[EnrolmentData])

  override def get(nino: String): EitherT[Future, String, EnrolmentStore.Status] = EitherT(
    {
      val timerContext = metrics.enrolmentStoreGetTimer.time()

      find("nino" → JsString(nino)).map { res ⇒
        val time = timerContext.stop()
        logger.info(s"For NINO [$nino]: GET on enrolment store took ${nanosToPrettyString(time)}")

        Right(res.headOption.fold[Status]{
          metrics.enrolmentStoreGetErrorCounter.inc()
          NotEnrolled
        }(data ⇒ Enrolled(data.itmpHtSFlag)))
      }.recover{
        case e ⇒
          val time = timerContext.stop()
          metrics.enrolmentStoreGetErrorCounter.inc()

          logger.error(s"For NINO [$nino]: Could not read from enrolment store (time: ${nanosToPrettyString(time)})", e)
          Left(s"For NINO [$nino]: Could not read from enrolment store: ${e.getMessage}")
      }
    })

  override def update(nino: NINO, itmpFlag: Boolean): EitherT[Future, String, Unit] = {
    logger.info(s"For NINO [$nino]: Updating entry into enrolment store (itmpFlag = $itmpFlag)")
    EitherT({
      val timerContext = metrics.enrolmentStoreUpdateTimer.time()

      doUpdate(nino, itmpFlag).map[Either[String, Unit]]{ result ⇒
        val time = timerContext.stop()

        result.fold[Either[String, Unit]] {
          metrics.enrolmentStoreUpdateErrorCounter.inc()
          Left(s"For NINO [$nino]: Could not update enrolment store (time: ${nanosToPrettyString(time)})")
        }{ _ ⇒
          logger.info(s"For NINO [$nino]: Successfully updated enrolment store (time: ${nanosToPrettyString(time)})")
          Right(())
        }
      }.recover{
        case e ⇒
          val time = timerContext.stop()
          metrics.enrolmentStoreUpdateErrorCounter.inc()

          logger.error(s"For NINO [$nino]: Could not write to enrolment store (time: ${nanosToPrettyString(time)})", e)
          Left(s"Failed to write to enrolments store: ${e.getMessage}")
      }
    }
    )
  }
}

object MongoEnrolmentStore {

  private[repo] case class EnrolmentData(nino: String, itmpHtSFlag: Boolean)

  private[repo] object EnrolmentData {
    implicit val ninoFormat: Format[EnrolmentData] = Json.format[EnrolmentData]
  }

}
