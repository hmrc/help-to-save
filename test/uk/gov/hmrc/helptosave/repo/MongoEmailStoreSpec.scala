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

import cats.syntax.all._
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import org.mongodb.scala.ObservableFuture

import scala.util.{Failure, Success, Try}

class MongoEmailStoreSpec extends TestSupport with Eventually with MongoSupport with BeforeAndAfterEach {

  lazy val repository: MongoEmailStore = newMongoEmailStore(mongoComponent)
  override def beforeEach(): Unit =
    await(repository.collection.drop().toFuture())

  val crypto: Crypto = mock[Crypto]

  def mockEncrypt(input: String)(output: String): Unit = {
    when(crypto.encrypt(input)).thenReturn(output)
  }

  def mockDecrypt(input: String)(output: Option[String]): Unit = {
    when(crypto.decrypt(input)).thenReturn(output.fold[Try[String]](Failure(new Exception("uh oh")))(Success(_)))
  }

  def newMongoEmailStore(mongoComponent: MongoComponent) =
    new MongoEmailStore(mongoComponent, crypto, mockMetrics)

  "The MongoEmailStore" when {
    val emailStore = repository

    val email = "EMAIL"
    val encryptedEmail = "ENCRYPTED"

    def storeConfirmedEmail(nino: NINO, email: String, emailStore: MongoEmailStore) =
      emailStore.store(email, nino)

    def getConfirmedEmail(nino: NINO, emailStore: MongoEmailStore) = emailStore.get(nino)

    def deleteEmail(nino: NINO, emailStore: MongoEmailStore) = emailStore.delete(nino)

    "updating emails" must {

      "store the email in the mongo database" in {
        val nino = randomNINO()

          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))

        val result = for {
          _           <- storeConfirmedEmail(nino, email, emailStore)
          storedEmail <- getConfirmedEmail(nino, emailStore)
          _           <- deleteEmail(nino, emailStore)
        } yield storedEmail

        await(result.value) shouldBe Right(email.some)
      }

      "update the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"
        val updatedEmail = "test@gmail.com"

          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
          mockEncrypt(updatedEmail)(encryptedEmail)

        //there should no emails to start with
        await(getConfirmedEmail(nino, emailStore).value) shouldBe Right(None)

        //putting the email in mongo
        await(storeConfirmedEmail(nino, email, emailStore).value)

        // try when there is an email
        await(getConfirmedEmail(nino, emailStore).value) shouldBe Right(email.some)

        // also try with nino with a different suffix
        await(storeConfirmedEmail(ninoDifferentSuffix, updatedEmail, emailStore).value) shouldBe Right(())

      }

    }

    "getting email" must {

      def get(nino: NINO, emailStore: MongoEmailStore): Either[String, Option[String]] =
        await(emailStore.get(nino).value)

      "return a right if the get is successful" in {
        val nino = randomNINO()

          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))

        //there should no emails to start with
        get(nino, emailStore) shouldBe Right(None)

        //putting the email in mongo
        await(storeConfirmedEmail(nino, email, emailStore).value)

        // try when there is an email
        get(nino, emailStore) shouldBe Right(Some(email))

        await(deleteEmail(nino, emailStore).value)

        //check the email has been removed
        eventually {
          get(nino, emailStore) shouldBe Right(None)
        }
      }

      "return the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val ninoDifferentSuffix = "AE123456B"

          mockEncrypt(email)(encryptedEmail)
          mockDecrypt(encryptedEmail)(Some(email))
          mockDecrypt(encryptedEmail)(Some(email))

        //there should no emails to start with
        get(nino, emailStore) shouldBe Right(None)

        //putting the email in mongo
        await(storeConfirmedEmail(nino, email, emailStore).value)

        // try when there is an email
        get(nino, emailStore) shouldBe Right(Some(email))

        // also try with nino with a different suffix
        get(ninoDifferentSuffix, emailStore) shouldBe Right(Some(email))
      }

    }

    "deleting emails" must {

      "delete the email in the mongo database for a given nino" in {
        val nino = randomNINO()

        mockEncrypt(email)(encryptedEmail)

        //store email first
        await(storeConfirmedEmail(nino, email, emailStore).value) shouldBe Right(())

        //then delete
        await(deleteEmail(nino, emailStore).value) shouldBe Right(())

        //now verify
        await(getConfirmedEmail(nino, emailStore).value) shouldBe Right(None)
      }

      "delete the email when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"

        mockEncrypt(email)(encryptedEmail)

        //store email first
        await(storeConfirmedEmail(nino, email, emailStore).value) shouldBe Right(())

        //then delete using a different suffix
        val ninoDifferentSuffix = "AE123456B"
        await(deleteEmail(ninoDifferentSuffix, emailStore).value) shouldBe Right(())

        //now verify using the original nino
        await(getConfirmedEmail(nino, emailStore).value) shouldBe Right(None)
      }

    }

  }

}
