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
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.{DataEncrypter, NINO}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class MongoEmailStoreSpec extends TestSupport with MongoTestSupport[EmailData, MongoEmailStore]{

  val mockDataEncrypter = mock[DataEncrypter]

  def newMongoStore() = new MongoEmailStore(mockMongo, mockDataEncrypter) {

    override def indexes: Seq[Index] = {
      // this line is to ensure scoverage picks up this line in MongoEnrolmentStore -
      // we can't really test the indexes function, it doesn't affect the behaviour of
      // the class only its performance
      super.indexes
      Seq.empty[Index]
    }

    override def doUpdate(nino: NINO, email: String)(implicit ec: ExecutionContext): Future[Option[EmailData]] =
      mockDBFunctions.update(EmailData(nino, email))
  }

  "The MongoEmailStore" when {

    "updating emails" must {

      val nino = "NINO"
      val email = "EMAIL"
      val data = EmailData(nino, email)

      def update(nino: NINO, email: String): Either[String, Unit] =
        Await.result(mongoStore.storeConfirmedEmail(nino, email).value, 5.seconds)

      "store the email in the mongo database" in {
//        mockEncrypt(email)(encryptedEmail)
        mockUpdate(data)(Right(None))
        update(nino, email)
      }

      "return a right if the update is successful" in {
        mockUpdate(data)(Right(Some(data)))
        update(nino, email) shouldBe Right(())
      }

      "return a left if the update is unsuccessful" in {
        mockUpdate(data)(Right(None))
        update(nino, email).isLeft shouldBe true

        mockUpdate(data)(Left(""))
        update(nino, email).isLeft shouldBe true
      }



    }


  }

}
