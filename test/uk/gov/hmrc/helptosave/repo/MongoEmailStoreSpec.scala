/*
 * Copyright 2017 HM Revenue & Customs
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

import reactivemongo.api.indexes.Index
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MongoEmailStoreSpec extends TestSupport with MongoTestSupport[EmailData, MongoEmailStore] {

  val crypto: Crypto = mock[Crypto]

  def mockEncrypt(input: String)(output: String): Unit =
    (crypto.encrypt(_: String))
      .expects(input)
      .returning(output)

  def newMongoStore() = new MongoEmailStore(mockMongo, crypto, mockMetrics) {

    override def indexes: Seq[Index] = {
      // this line is to ensure scoverage picks up this line in MongoEnrolmentStore -
      // we can't really test the indexes function, it doesn't affect the behaviour of
      // the class only its performance
      super.indexes
      Seq.empty[Index]
    }

    override def doUpdate(encryptedEmail: String, nino: NINO)(implicit ec: ExecutionContext): Future[Option[EmailData]] =
      mockDBFunctions.update(EmailData(nino, encryptedEmail))
  }

  "The MongoEmailStore" when {

    "updating emails" must {

      val nino = "NINO"
      val email = "EMAIL"
      val encryptedEmail = "ENCRYPTED"
      val data = EmailData(nino, encryptedEmail)

        def update(nino: NINO, email: String): Either[String, Unit] =
          Await.result(mongoStore.storeConfirmedEmail(email, nino).value, 5.seconds)

      "store the email in the mongo database" in {
        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockUpdate(data)(Right(None))
        }

        update(nino, email)
      }

      "return a right if the update is successful" in {
        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockUpdate(data)(Right(Some(data)))
        }

        update(nino, email) shouldBe Right(())
      }

      "return a left if the update is unsuccessful" in {
        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockUpdate(data)(Right(None))
        }
        update(nino, email).isLeft shouldBe true

        inSequence {
          mockEncrypt(email)(encryptedEmail)
          mockUpdate(data)(Left(""))
        }
        update(nino, email).isLeft shouldBe true
      }

    }

  }

}
