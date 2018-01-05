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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.helptosave.repo.UserCapStore.{UserCap, dateFormat}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoUserCapStore])
trait UserCapStore {
  def get(): Future[Option[UserCap]]

  def upsert(userCap: UserCap): Future[Option[UserCap]]
}

@Singleton
class MongoUserCapStore @Inject() (mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[UserCap, BSONObjectID](
    collectionName = "usercap",
    mongo          = mongo.mongoConnector.db,
    UserCap.userCapFormat,
    ReactiveMongoFormats.objectIdFormats)
  with UserCapStore {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("usercap" → IndexType.Ascending),
      name = Some("usercapIndex")
    )
  )

  private[repo] def doFind(): Future[Option[UserCap]] = collection.find(Json.obj()).one[UserCap]

  override def get(): Future[Option[UserCap]] = doFind()

  private[repo] def doUpdate(userCap: UserCap): Future[Option[UserCap]] = {
    collection.findAndUpdate(
      BSONDocument(),
      BSONDocument("$set" -> BSONDocument("date" -> dateFormat.format(userCap.date), "dailyCount" -> userCap.dailyCount, "totalCount" -> userCap.totalCount)),
      fetchNewObject = true,
      upsert         = true
    ).map(_.result[UserCap])
  }

  override def upsert(userCap: UserCap): Future[Option[UserCap]] = doUpdate(userCap)
}

object UserCapStore {

  val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  case class UserCap(date: LocalDate = LocalDate.now(), dailyCount: Int, totalCount: Int) {

    def isTodaysRecord: Boolean = LocalDate.now().isEqual(date)

    def isPreviousRecord: Boolean = !isTodaysRecord
  }

  object UserCap {

    def apply(dailyCount: Int, totalCount: Int): UserCap =
      new UserCap(LocalDate.now(), dailyCount, totalCount)

    implicit val userCapFormat: Format[UserCap] = Json.format[UserCap]
  }

}
