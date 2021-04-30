/*
 * Copyright 2021 HM Revenue & Customs
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
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport

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

    val email = "EMAIL"
    val encryptedEmail = "ENCRYPTED"

      def storeConfirmedEmail(nino: NINO, email: String, emailStore: MongoEmailStore): Either[String, Unit] =
        await(emailStore.store(email, nino).value)

      def getConfirmedEmail(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
        await(emailStore.get(nino).value)

      def deleteEmail(nino: NINO, emailStore: MongoEmailStore): Either[String, Unit] =
        await(emailStore.delete(nino).value)

    "updating emails" must {

      "store the email in the mongo database" in {
        val nino = randomNINO()
        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
        }

        storeConfirmedEmail(nino, email, emailStore)

        val storedEmail = getConfirmedEmail(nino, emailStore)
        storedEmail shouldBe Right(Some(email))

      }

      "return a right if the update is successful" in {
        val nino = randomNINO()
        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        inSequence {
          mockEncrypt(email)(encryptedEmail)
        }

        val result = storeConfirmedEmail(nino, email, emailStore)
        result shouldBe Right(())
      }

      "return a left if the update is unsuccessful" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val nino = randomNINO()
          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          inSequence {
            mockEncrypt(email)(encryptedEmail)
          }

          storeConfirmedEmail(nino, email, emailStore).isLeft shouldBe true
        }
      }

      "update the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"
        val updatedEmail = "test@gmail.com"

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
          mockEncrypt(updatedEmail)(encryptedEmail)
        }

        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        //there should no emails to start with
        getConfirmedEmail(nino, emailStore) shouldBe Right(None)

        //putting the email in mongo
        storeConfirmedEmail(nino, email, emailStore)

        // try when there is an email
        getConfirmedEmail(nino, emailStore) shouldBe Right(Some(email))

        // also try with nino with a different suffix
        storeConfirmedEmail(ninoDifferentSuffix, updatedEmail, emailStore) shouldBe Right(())
      }

    }

    "getting email" must {

      import reactivemongo.play.json.ImplicitBSONHandlers._

        def get(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
          await(emailStore.get(nino).value)

        def remove(nino: String)(collection: JSONCollection): Unit = {
          val selector = JsObject(Map("nino" → JsString(nino)))
          collection.delete().one(selector).value
        }

      "return a right if the get is successful" in {
        val nino = randomNINO()

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
        }

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

      "return a left if the get is unsuccessful" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val nino = randomNINO()
          val emailStore = newMongoEmailStore(reactiveMongoComponent)
          get(nino, emailStore).isLeft shouldBe true
        }
      }

      "return the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
          mockDecrypt(encryptedEmail)(Some(email))
        }

        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        //there should no emails to start with
        get(nino, emailStore) shouldBe Right(None)

        //putting the email in mongo
        storeConfirmedEmail(nino, email, emailStore)

        // try when there is an email
        get(nino, emailStore) shouldBe Right(Some(email))

        // also try with nino with a different suffix
        get(ninoDifferentSuffix, emailStore) shouldBe Right(Some(email))
      }

    }

    "deleting emails" must {

      "delete the email in the mongo database for a given nino" in {
        val nino = randomNINO()
        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        mockEncrypt(email)(encryptedEmail)

        //store email first
        val result = storeConfirmedEmail(nino, email, emailStore)
        result shouldBe Right(())

        //then delete
        deleteEmail(nino, emailStore) shouldBe Right(())

        //now verify
        getConfirmedEmail(nino, emailStore) shouldBe Right(None)
      }

      "return a left if the delete is unsuccessful" in {
        withBrokenMongo { reactiveMongoComponent ⇒
          val nino = randomNINO()
          val emailStore = newMongoEmailStore(reactiveMongoComponent)

          deleteEmail(nino, emailStore).isLeft shouldBe true
        }
      }

      "delete the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val emailStore = newMongoEmailStore(reactiveMongoComponent)

        mockEncrypt(email)(encryptedEmail)

        //store email first
        val result = storeConfirmedEmail(nino, email, emailStore)
        result shouldBe Right(())

        //then delete using a different suffix
        val ninoDifferentSuffix = "AE123456B"
        deleteEmail(ninoDifferentSuffix, emailStore) shouldBe Right(())

        //now verify using the original nino
        getConfirmedEmail(nino, emailStore) shouldBe Right(None)
      }

    }

  }

}
