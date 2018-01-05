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

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{MongoConnector, ReactiveRepository}

import scala.concurrent.Future

trait MongoTestSupport[Data, Repo <: ReactiveRepository[Data, BSONObjectID]] {
  this: MockFactory ⇒

  trait MockDBFunctions {
    def update(data: Data): Future[Option[Data]]

    def get[ID](id: ID): Future[List[Data]]

    def get(): Future[Option[Data]]

    def remove(data: Data): Future[Option[Data]]
  }

  val mockDBFunctions = mock[MockDBFunctions]

  val mockMongo = mock[ReactiveMongoComponent]

  def newMongoStore(): Repo

  lazy val mongoStore: Repo = {
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mockMongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)
    newMongoStore()
  }

  def mockUpdate(data: Data)(result: Either[String, Option[Data]]): Unit =
    (mockDBFunctions.update(_: Data))
      .expects(data)
      .returning(result.fold(
        s ⇒ Future.failed(new Exception(s)),
        Future.successful)
      )

  def mockFind(id: String)(result: ⇒ Future[List[Data]]): Unit =
    (mockDBFunctions.get[Json.JsValueWrapper](_: Json.JsValueWrapper))
      .expects(toJsFieldJsValueWrapper(JsString(id)))
      .returning(result)

  def mockGet()(result: ⇒ Future[Option[Data]]): Unit =
    (mockDBFunctions.get: () ⇒ Future[Option[Data]]).expects()
      .returning(result)
}
