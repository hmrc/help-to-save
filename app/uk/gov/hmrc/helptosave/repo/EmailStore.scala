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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[MongoEmailStore])
trait EmailStore {

  def storeConfirmedEmail(email: String, nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit]

}

@Singleton
class MongoEmailStore @Inject() (mongo:   ReactiveMongoComponent,
                                 crypto:  Crypto,
                                 metrics: Metrics)
  extends ReactiveRepository[EmailData, BSONObjectID](
    collectionName = "emails",
    mongo          = mongo.mongoConnector.db,
    EmailData.emailDataFormat,
    ReactiveMongoFormats.objectIdFormats)
  with EmailStore {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  def storeConfirmedEmail(email: String, nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT[Future, String, Unit]({
      val timerContext = metrics.emailStoreUpdateTimer.time()

      doUpdate(crypto.encrypt(email), nino)
        .map{ result ⇒
          val time = timerContext.stop()
          logger.info(s"Update on email store completed (successful: ${result.isDefined}) (time: ${nanosToPrettyString(time)})")

          result.fold[Either[String, Unit]](
            Left("Could not update email mongo store"))(
              _ ⇒ Right(()))
        }
        .recover{
          case NonFatal(e) ⇒
            val time = timerContext.stop()
            Left(s"${e.getMessage} (time: ${nanosToPrettyString(time)})")
        }
    })

  private[repo] def doUpdate(encryptedEmail: String, nino: NINO)(implicit ec: ExecutionContext): Future[Option[EmailData]] =
    collection.findAndUpdate(
      BSONDocument("nino" -> nino),
      BSONDocument("$set" -> BSONDocument("email" -> encryptedEmail)),
      fetchNewObject = true,
      upsert         = true
    ).map(_.result[EmailData])

}

object MongoEmailStore {

  private[repo] case class EmailData(nino: String, email: String)

  private[repo] object EmailData {
    implicit val emailDataFormat: Format[EmailData] = Json.format[EmailData]
  }

}
