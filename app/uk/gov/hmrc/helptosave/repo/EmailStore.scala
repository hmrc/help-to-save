/*
 * Copyright 2019 HM Revenue & Customs
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
import cats.instances.option._
import cats.instances.try_._
import cats.syntax.either._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{Format, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.helptosave.util.{Crypto, LogMessageTransformer, NINO}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[MongoEmailStore])
trait EmailStore {

  def store(email: String, nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit]

  def get(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Option[String]]

  def delete(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit]

}

@Singleton
class MongoEmailStore @Inject() (mongo:   ReactiveMongoComponent,
                                 crypto:  Crypto,
                                 metrics: Metrics)(
    implicit
    transformer: LogMessageTransformer)
  extends ReactiveRepository[EmailData, BSONObjectID](
    collectionName = "emails",
    mongo          = mongo.mongoConnector.db,
    EmailData.emailDataFormat,
    ReactiveMongoFormats.objectIdFormats)
  with EmailStore {

  val log: Logger = new Logger(logger)

  def getRegex(nino: String): String = "^" + nino.take(8) + ".$"

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  def store(email: String, nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT[Future, String, Unit]({
      val timerContext = metrics.emailStoreUpdateTimer.time()

      doUpdate(crypto.encrypt(email), nino)
        .map[Either[String, Unit]] { result ⇒
          val time = timerContext.stop()

          if (!result) {
            metrics.emailStoreUpdateErrorCounter.inc()
            Left("Could not update email mongo store")
          } else { Right(()) }
        }
        .recover {
          case NonFatal(e) ⇒
            val time = timerContext.stop()
            metrics.emailStoreUpdateErrorCounter.inc()
            Left(s"${e.getMessage}")
        }
    })

  override def get(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Option[String]] = EitherT[Future, String, Option[String]]({
    val timerContext = metrics.emailStoreGetTimer.time()

    find("nino" → Json.obj("$regex" → JsString(getRegex(nino)))).map { res ⇒
      val time = timerContext.stop()

      val decryptedEmail = res.headOption
        .map(data ⇒ crypto.decrypt(data.email))
        .traverse[Try, String](identity)

      decryptedEmail.toEither().leftMap {
        t ⇒
          log.warn("Could not decrypt email", t, nino)
          s"Could not decrypt email: ${t.getMessage}"
      }
    }.recover {
      case e ⇒
        val time = timerContext.stop()
        metrics.emailStoreGetErrorCounter.inc()
        Left(s"Could not read from email store: ${e.getMessage}")
    }
  })

  override def delete(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] = EitherT[Future, String, Unit]{

    remove("nino" → Json.obj("$regex" → JsString(getRegex(nino)))).map[Either[String, Unit]]{ res ⇒
      if (res.writeErrors.nonEmpty) {
        Left(s"Could not delete email: ${res.writeErrors.mkString(";")}")
      } else {
        Right(())
      }
    }.recover{
      case e ⇒
        Left(s"Could not delete email: ${e.getMessage}")
    }

  }

  private[repo] def doUpdate(encryptedEmail: String, nino: NINO)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(
      BSONDocument("nino" -> BSONDocument("$regex" -> getRegex(nino))),
      BSONDocument("$set" -> BSONDocument("nino" -> nino, "email" -> encryptedEmail)),
      upsert = true
    ).map(_.ok)

}

object MongoEmailStore {

  private[repo] case class EmailData(nino: String, email: String)

  private[repo] object EmailData {
    implicit val emailDataFormat: Format[EmailData] = Json.format[EmailData]
  }

}
