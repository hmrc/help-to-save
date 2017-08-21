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
import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEnrolmentStore])
trait EnrolmentStore {
  import EnrolmentStore._

  def get(nino: NINO): EitherT[Future,String,Status]

  def update(nino: NINO, itmpFlag: Boolean): EitherT[Future,String,Unit]

}

object EnrolmentStore {

  sealed trait Status

  case class Enrolled(itmpHtSFlag: Boolean) extends Status

  case object NotEnrolled extends Status

}

class MongoEnrolmentStore @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[EnrolmentData, BSONObjectID] (
    collectionName = "enrolments",
    mongo = mongo.mongoConnector.db,
    EnrolmentData.ninoFormat,
    ReactiveMongoFormats.objectIdFormats)
    with EnrolmentStore {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  private[repo] def doUpdate(nino: NINO, itmpFlag: Boolean)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
    collection.findAndUpdate(
      BSONDocument("nino" -> nino),
      BSONDocument("$set" -> BSONDocument("itmpHtSFlag" -> itmpFlag)),
      fetchNewObject = true,
      upsert = true
    ).map(_.result[EnrolmentData])

  override def get(nino: String): EitherT[Future, String, EnrolmentStore.Status] = EitherT(
    find("nino" → JsString(nino)).map { res ⇒
      Right(res.headOption.fold[Status](NotEnrolled)(data ⇒ Enrolled(data.itmpHtSFlag)))
    }.recover{
      case e ⇒
        logger.error(s"Could not read from enrolment store", e)
        Left(s"Could not read from enrolment store: ${e.getMessage}")
    })

  override def update(nino: NINO, itmpFlag: Boolean): EitherT[Future, String, Unit] = {
    logger.info(s"Putting nino $nino into enrolment store")
    EitherT(
      doUpdate(nino, itmpFlag).map[Either[String,Unit]]{ result ⇒
        result.fold[Either[String,Unit]](
          Left("Could not update enrolment store")
        ){ _ ⇒
          logger.info("Successfully updated enrolment store")
          Right(())
        }
      }.recover{ case e ⇒
        logger.error(s"Could not write to enrolment store", e)
        Left(s"Failed to write to enrolments store: ${e.getMessage}")
      }
    )
  }
}

object MongoEnrolmentStore {

  private[repo] case class EnrolmentData(nino: String, itmpHtSFlag: Boolean)

  private[repo]  object EnrolmentData {
    implicit val ninoFormat = Json.format[EnrolmentData]
  }

}