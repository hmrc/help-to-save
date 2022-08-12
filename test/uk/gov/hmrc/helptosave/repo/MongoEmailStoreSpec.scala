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

import cats.data.EitherT
import cats.syntax.all._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters
import org.scalatest.Succeeded
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class MongoEmailStoreSpec extends TestSupport with Eventually with MongoSupport {
  val repository: MongoEmailStore = fakeApplication.injector.instanceOf[MongoEmailStore]

  val crypto: Crypto = mock[Crypto]

  def mockEncrypt(input: String)(output: String): Unit =
    (crypto.encrypt(_: String))
      .expects(input)
      .returning(output)

  def mockDecrypt(input: String)(output: Option[String]): Unit =
    (crypto.decrypt(_: String))
      .expects(input)
      .returning(output.fold[Try[String]](Failure(new Exception("uh oh")))(Success(_)))

  def newMongoEmailStore(mongoComponent: MongoComponent) =
    new MongoEmailStore(mongoComponent, crypto, mockMetrics)

  "The MongoEmailStore" when {

    val email = "EMAIL"
    val encryptedEmail = "ENCRYPTED"

      def storeConfirmedEmail(nino: NINO, email: String, emailStore: MongoEmailStore) =
        emailStore.store(email, nino)

      def getConfirmedEmail(nino: NINO, emailStore: MongoEmailStore) = emailStore.get(nino)

      def deleteEmail(nino: NINO, emailStore: MongoEmailStore) = emailStore.delete(nino)

      def mockEncryptEitherT(emailOpt: Option[String] = None) =
        EitherT[Future, String, Unit](Future.successful(Right(mockEncrypt(emailOpt.getOrElse(email))(encryptedEmail))))

      def mockDecryptEitherT(emailOpt: Option[String] = None) = EitherT[Future, String, Unit](Future.successful(Right(mockDecrypt(encryptedEmail)(emailOpt))))

    "updating emails" must {

      "store the email in the mongo database" in {
        val nino = randomNINO()
        val emailStore = newMongoEmailStore(mongoComponent)

        val result = for {
          _ ← mockEncryptEitherT()
          _ ← storeConfirmedEmail(nino, email, emailStore)
          //          _ ← mockDecryptEitherT()
          storedEmail ← getConfirmedEmail(nino, emailStore)
        } yield storedEmail

        await(result.value) shouldBe Right(email.some)
      }

      "return a right if the update is successful" in {
        val nino = randomNINO()
        val emailStore = newMongoEmailStore(mongoComponent)

        val result = for {
          _ ← mockEncryptEitherT()
          update ← storeConfirmedEmail(nino, email, emailStore)
        } yield (update shouldBe ())

        await(result.value) shouldBe true
      }

      "return a left if the update is unsuccessful" in {

        val nino = randomNINO()
        val emailStore = newMongoEmailStore(mongoComponent)

        val result = for {
          _ ← mockEncryptEitherT()
          update ← storeConfirmedEmail(nino, email, emailStore)
        } yield (update shouldBe ())

        await(result.value)

      }

      "update the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"
        val updatedEmail = "test@gmail.com"
        val emailStore = newMongoEmailStore(mongoComponent)
        //
        //        val result = for {
        //          noEmails ← getConfirmedEmail(nino, emailStore)
        //          _ = println("************")
        //          _ = println(noEmails)
        //          _ ← mockEncryptEitherT()
        //          _ ← storeConfirmedEmail(nino, email, emailStore)
        //          _ ← mockDecryptEitherT()
        //          emailUpdate ← getConfirmedEmail(nino, emailStore)
        //          _ ← mockEncryptEitherT(Some(updatedEmail))
        //          _ ← getConfirmedEmail(nino, emailStore)
        //          update ← storeConfirmedEmail(ninoDifferentSuffix, updatedEmail, emailStore)
        //        } yield {
        //          (
        //            noEmails.isEmpty,
        //            emailUpdate.isDefined,
        //            update,
        //          )
        //        }
        //
        //        await(result.value)

        //there should no emails to start with

        val result = for {
          noEmail ← getConfirmedEmail(nino, emailStore)
          _ ← mockDecryptEitherT()
          //          _ ← storeConfirmedEmail(nino, email, emailStore)
          _ ← mockDecryptEitherT()
          emailUpdate ← getConfirmedEmail(nino, emailStore)
          _ ← mockDecryptEitherT(Some(email))
          res ← storeConfirmedEmail(ninoDifferentSuffix, updatedEmail, emailStore)
        } yield {
          noEmail shouldBe Some("")
          //            emailUpdate contains email,
          //            res shouldBe ())
        }

        await(result.value) == Right(Succeeded)

        //putting the email in mongo

        // try when there is an email

        // also try with nino with a different suffix
      }

    }

    "getting email" must {

        def get(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
          await(emailStore.get(nino).value)

        def remove(nino: String)(collection: MongoCollection[MongoEmailStore.EmailData]): Unit = {
          //          val selector = JsObject(Map("nino" → JsString(nino)))
          collection.findOneAndDelete(Filters.equal("nino", nino))
          //          collection.delete().one(selector).value
        }

      "return a right if the get is successful" in {
        val nino = randomNINO()

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
        }

        val emailStore = newMongoEmailStore(mongoComponent)

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

        val nino = randomNINO()
        val emailStore = newMongoEmailStore(mongoComponent)
        get(nino, emailStore).isLeft shouldBe true

      }

      "return the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
          mockDecrypt(encryptedEmail)(Some(email))
        }

        val emailStore = newMongoEmailStore(mongoComponent)

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
        val emailStore = newMongoEmailStore(mongoComponent)

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

        val nino = randomNINO()
        val emailStore = newMongoEmailStore(mongoComponent)

        deleteEmail(nino, emailStore).isLeft shouldBe true

      }

      "delete the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val emailStore = newMongoEmailStore(mongoComponent)

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
