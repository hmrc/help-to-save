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

///*
// * Copyright 2022 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.helptosave.repo
//
//import org.mongodb.scala.{MongoClient, MongoDatabase, MongoSocketOpenException}
//import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
//import play.modules.reactivemongo.ReactiveMongoComponent
//import reactivemongo.api.FailoverStrategy
//import reactivemongo.core.actors.Exceptions.PrimaryUnavailableException
//import uk.gov.hmrc.mongo.{MongoComponent, MongoConnector}
//
//trait MongoSupport extends BeforeAndAfterEach with BeforeAndAfterAll { this: Suite ⇒
//
//  val mongoComponent: MongoComponent = new MongoComponent {
//    override def client: MongoClient = MongoClient(s"mongodb://127.0.0.1:27018/mongodb")
//
//    override def database: MongoDatabase = client.getDatabase("mongodb")
//  }
//
//  //    new ReactiveMongoComponent {
//  //    override def mongoConnector: MongoConnector = mongoConnectorForTest
//  //  }
//
//  def withBrokenMongo(f: MongoComponent ⇒ Unit): Unit =
//    scala.util.control.Exception.ignoring(classOf[MongoSocketOpenException]) {
//      try {
//        f(mongoComponent)
//      } finally {
//        mongoComponent.client.close()
//      }
//    }
//
//  abstract override def beforeEach(): Unit = {
//    super.beforeEach()
//    mongoComponent.database.drop()
//  }
//
//  abstract override def afterAll(): Unit = {
//    super.afterAll()
//    mongoComponent.client.close()
//  }
//
//}
