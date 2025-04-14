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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import uk.gov.hmrc.helptosave.models.UserCap
import uk.gov.hmrc.helptosave.models.UserCap.dateFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoUserCapStore])
trait UserCapStore {
  def get(): Future[Option[UserCap]]

  def upsert(userCap: UserCap): Future[Option[UserCap]]
}

@Singleton
class MongoUserCapStore @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UserCap](
      mongoComponent = mongo,
      collectionName = "usercap",
      domainFormat = UserCap.userCapFormat,
      indexes = Seq(IndexModel(ascending("usercap"), IndexOptions().name("usercapIndex")))
    )
    with UserCapStore {
  private[repo] def doFind(): Future[Option[UserCap]] =
    preservingMdc {
      collection.find().headOption()
    }

  override def get(): Future[Option[UserCap]] = doFind()

  private[repo] def doUpdate(userCap: UserCap): Future[Option[UserCap]] =
    preservingMdc {
      collection
        .findOneAndUpdate(
          filter = BsonDocument(),
          update = Updates.combine(
            Updates.set("date", dateFormat.format(userCap.date)),
            Updates.set("dailyCount", userCap.dailyCount),
            Updates.set("totalCount", userCap.totalCount)
          ),
          options =
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true).bypassDocumentValidation(false)
        )
        .toFutureOption()
    }

  override def upsert(userCap: UserCap): Future[Option[UserCap]] = doUpdate(userCap)
}
