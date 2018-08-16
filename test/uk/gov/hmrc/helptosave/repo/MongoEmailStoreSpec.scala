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

import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsObject, JsString}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class MongoEmailStoreSpec extends TestSupport with Eventually with MongoSupport {

  val crypto: Crypto = mock[Crypto]

  def mockEncrypt(input: String)(output: String): Unit =
    (crypto.encrypt(_: String))
      .expects(input)
      .returning(output)

  def mockDecrypt(input: String)(output: Option[String]): Unit =
    (crypto.decrypt(_: String))
      .expects(input)
      .returning(output.fold[Try[String]](Failure(new Exception("uh oh")))(Success(_)))

  def newMongoEmailStore(reactiveMongoComponent: ReactiveMongoComponent) =
    new MongoEmailStore(reactiveMongoComponent, crypto, mockMetrics) {

    }

  "The MongoEmailStore" when {

    val nino = "NINO"
    val email = "EMAIL"
    val encryptedEmail = "ENCRYPTED"

      def storeConfirmedEmail(nino: NINO, email: String, emailStore: MongoEmailStore): Either[String, Unit] =
        Await.result(emailStore.storeConfirmedEmail(email, nino).value, 5.seconds)

      def getConfirmedEmail(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
        Await.result(emailStore.getConfirmedEmail(nino).value, 5.seconds)

    "updating emails" must {

      val data = EmailData(nino, encryptedEmail)

      "store the email in the mongo database" in {
        withMongo { reactiveMongoComponent ⇒
          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          inSequence {
            mockEncrypt(email)(encryptedEmail)
            mockDecrypt(encryptedEmail)(Some(email))
          }

          storeConfirmedEmail(nino, email, emailStore)

          val storedEmail = getConfirmedEmail(nino, emailStore)
          storedEmail shouldBe Right(Some(email))
        }

      }

      "return a right if the update is successful" in {
        withMongo { reactiveMongoComponent ⇒
          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          inSequence {
            mockEncrypt(email)(encryptedEmail)
          }

          val result = storeConfirmedEmail(nino, email, emailStore)
          result shouldBe Right(())
        }
      }

      "return a left if the update is unsuccessful" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          inSequence {
            mockEncrypt(email)(encryptedEmail)
          }

          storeConfirmedEmail(nino, email, emailStore).isLeft shouldBe true
        }
      }

    }

    "getting email" must {

      import reactivemongo.play.json.ImplicitBSONHandlers._

        def get(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
          Await.result(emailStore.getConfirmedEmail(nino).value, 5.seconds)

        def remove(nino: String)(collection: JSONCollection): Unit = {
          val selector = JsObject(Map("nino" → JsString(nino)))
          Await.result(collection.remove(selector).value, 5.seconds)
        }

      "return a right if the get is successful" in {
        withMongo { reactiveMongoComponent ⇒
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))

          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          //there should no emails to start with
          get(nino, emailStore) shouldBe Right(None)

          //putting the email in mongo
          storeConfirmedEmail(nino, email, emailStore)

          // try when there is an email
          get(nino, emailStore) shouldBe Right(Some(email))

          remove(nino)(emailStore.collection)

          //check the email has been removed
          eventually {
            get(nino, emailStore) shouldBe Right(None)
          }
        }
      }

      "return a left if the get is unsuccessful" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val emailStore = newMongoEmailStore(reactiveMongoComponent)
          get(nino, emailStore).isLeft shouldBe true
        }
      }

    }

  }

}
