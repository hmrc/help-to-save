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

package uk.gov.hmrc.helptosave.repo

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.util.LogMessageTransformer
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[MongoThresholdStore])
trait ThresholdStore {

  def storeUCThreshold(amount: Double)(implicit ec: ExecutionContext): EitherT[Future, String, Unit]

  def getUCThreshold()(implicit ec: ExecutionContext): EitherT[Future, String, Option[Double]]
}

@Singleton
class MongoThresholdStore @Inject() (mongo: ReactiveMongoComponent)(implicit transformer: LogMessageTransformer)
  extends ReactiveRepository[UCThreshold, BSONObjectID](
    collectionName = "threshold",
    mongo          = mongo.mongoConnector.db,
    UCThreshold.format,
    ReactiveMongoFormats.objectIdFormats)
  with ThresholdStore {

  val log: Logger = new Logger(logger)

  def storeUCThreshold(amount: Double)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    EitherT[Future, String, Unit]({
      doUpdate(amount)
        .map {
          res ⇒
            res.fold[Either[String, Unit]] {
              Left("Could not update the threshold mongo store with new UC threshold value")
            }(_ ⇒ Right(()))
        }.recover {
          case NonFatal(e) ⇒
            Left(s"${e.getMessage}")
        }
    })

  def getUCThreshold()(implicit ec: ExecutionContext): EitherT[Future, String, Option[Double]] =
    EitherT[Future, String, Option[Double]]({
      findAll().map { res ⇒
        Right(res.headOption
          .map(data ⇒ data.thresholdAmount))
      }.recover {
        case e ⇒
          Left(s"Could not read UC threshold value from threshold store: ${e.getMessage}")
      }
    })

  private[repo] def doUpdate(amount: Double)(implicit ec: ExecutionContext): Future[Option[UCThreshold]] =
    collection.findAndUpdate(
      BSONDocument(),
      BSONDocument("$set" -> BSONDocument("thresholdAmount" -> amount)),
      fetchNewObject = true,
      upsert         = true
    ).map(_.result[UCThreshold])

}
