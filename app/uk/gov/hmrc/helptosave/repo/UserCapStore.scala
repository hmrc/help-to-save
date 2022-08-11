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
import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.repo.UserCapStore.{UserCap, dateFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId}
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
    domainFormat   = UserCap.userCapFormat,
    indexes        = Seq(IndexModel(ascending("usercap"), IndexOptions().name("usercapIndex")))
  )
  with UserCapStore {

  //  override def indexes: Seq[Index] = Seq(
  //    Index(
  //      key  = Seq("usercap" → IndexType.Ascending),
  //      name = Some("usercapIndex")
  //    )
  //  )

  private[repo] def doFind(): Future[Option[UserCap]] = collection.find().headOption() //collection.find(BSONDocument(), None).one[UserCap]

  override def get(): Future[Option[UserCap]] = doFind()

  private[repo] def doUpdate(userCap: UserCap): Future[Option[UserCap]] = {
    collection.findOneAndUpdate(
      filter  = BsonDocument(),
      update  = Updates.combine(
        Updates.set("date", dateFormat.format(userCap.date)),
        Updates.set("dailyCount", userCap.dailyCount),
        Updates.set("totalCount", userCap.totalCount)
      ),
      options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true).bypassDocumentValidation(false)
    ).toFutureOption()

    //    collection.findAndUpdate(
    //      BSONDocument(),
    //      BSONDocument("$set" -> BSONDocument("date" -> dateFormat.format(userCap.date), "dailyCount" -> userCap.dailyCount, "totalCount" -> userCap.totalCount)),
    //      fetchNewObject           = true,
    //      upsert                   = true,
    //      sort                     = None,
    //      fields                   = None,
    //      bypassDocumentValidation = false,
    //      writeConcern             = WriteConcern.Default,
    //      maxTime                  = None,
    //      collation                = None,
    //      arrayFilters             = Seq()
    //    ).map(_.result[UserCap])
  }

  override def upsert(userCap: UserCap): Future[Option[UserCap]] = doUpdate(userCap)
}

object UserCapStore {

  val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  private val utcZone: ZoneId = ZoneId.of("Z")

  case class UserCap(date: LocalDate = LocalDate.now(utcZone), dailyCount: Int, totalCount: Int) {

    def isTodaysRecord: Boolean = LocalDate.now(utcZone).isEqual(date)

    def isPreviousRecord: Boolean = !isTodaysRecord
  }

  object UserCap {

    def apply(dailyCount: Int, totalCount: Int): UserCap =
      new UserCap(LocalDate.now(utcZone), dailyCount, totalCount)

    implicit val userCapFormat: Format[UserCap] = Json.format[UserCap]
  }

}
