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

package uk.gov.hmrc.helptosave.repo

import cats.data.EitherT
import cats.instances.option._
import cats.instances.try_._
import cats.syntax.either._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.model.Filters.regex
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions, Updates}
import play.api.Logging
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

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
class MongoEmailStore @Inject()(mongo: MongoComponent, crypto: Crypto, metrics: Metrics)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[EmailData](
      mongoComponent = mongo,
      collectionName = "emails",
      domainFormat = EmailData.emailDataFormat,
      indexes = Seq(IndexModel(ascending("nino"), IndexOptions().name("ninoIndex")))
    ) with EmailStore with Logging {

  def getRegex(nino: String): String = "^" + nino.take(8) + ".$"

  def store(email: String, nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT[Future, String, Unit]({
      val timerContext = metrics.emailStoreUpdateTimer.time()

      doUpdate(crypto.encrypt(email), nino)
        .map[Either[String, Unit]] { result =>
          timerContext.stop()

          if (!result) {
            metrics.emailStoreUpdateErrorCounter.inc()
            Left("Could not update email mongo store")
          } else {
            Right(())
          }
        }
        .recover {
          case NonFatal(e) =>
            timerContext.stop()
            metrics.emailStoreUpdateErrorCounter.inc()
            Left(s"${e.getMessage}")
        }
    })

  override def get(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Option[String]] =
    EitherT[Future, String, Option[String]]({
      preservingMdc {
        val timerContext = metrics.emailStoreGetTimer.time()

        collection
          .find(regex("nino", getRegex(nino)))
          .toFuture()
          .map { res =>
            timerContext.stop()

            val decryptedEmail = res.headOption
              .map(data => crypto.decrypt(data.email))
              .traverse[Try, String](identity)

            decryptedEmail.toEither().leftMap { t =>
              logger.warn(s"Could not decrypt email: $t, $nino")
              s"Could not decrypt email: ${t.getMessage}"
            }
          }
          .recover {
            case e =>
              timerContext.stop()
              metrics.emailStoreGetErrorCounter.inc()
              Left(s"Could not read from email store: ${e.getMessage}")
          }
      }
    })

  override def delete(nino: NINO)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT[Future, String, Unit] {
      preservingMdc {
        collection
          .findOneAndDelete(regex("nino", getRegex(nino)))
          .toFuture()
          .map[Either[String, Unit]] { _ =>
            Right(())
          }
          .recover {
            case e =>
              Left(s"Could not delete email: ${e.getMessage}")
          }
      }
    }

  private[repo] def doUpdate(encryptedEmail: String, nino: NINO)(implicit ec: ExecutionContext): Future[Boolean] =
    preservingMdc {
      collection
        .updateOne(
          filter = regex("nino", getRegex(nino)),
          update = Updates.combine(Updates.set("nino", nino), Updates.set("email", encryptedEmail)),
          options = UpdateOptions().upsert(true)
        )
        .toFuture()
        .map(a => {
          a.wasAcknowledged()
        })
    }
}

object MongoEmailStore {
  private[repo] case class EmailData(nino: String, email: String)

  private[repo] object EmailData {
    implicit val emailDataFormat: Format[EmailData] = Json.format[EmailData]
  }
}
