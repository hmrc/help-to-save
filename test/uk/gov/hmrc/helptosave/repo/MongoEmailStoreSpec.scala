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

import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.helptosave.repo.MongoEmailStore.EmailData
import uk.gov.hmrc.helptosave.util.{Crypto, NINO}
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MongoEmailStoreSpec extends TestSupport with MongoTestSupport[EmailData, MongoEmailStore] {

  val crypto: Crypto = mock[Crypto]

  def mockEncrypt(input: String)(output: String): Unit =
    (crypto.encrypt(_: String))
      .expects(input)
      .returning(output)

  def mockDecrypt(input: String)(output: Option[String]): Unit =
    (crypto.decrypt(_: String))
      .expects(input)
      .returning(output.fold[Try[String]](Failure(new Exception("uh oh")))(Success(_)))

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

    override def find(query: (String, Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[EmailData]] =
      query.toList match {
        case head :: Nil ⇒ mockDBFunctions.get(head._2)
        case _           ⇒ fail("Find function expected only 1 parameter")
      }
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

    "getting email" must {

      val nino = "NINO"
      val email = "EMAIL"

        def get(nino: NINO): Either[String, Option[String]] =
          Await.result(mongoStore.getConfirmedEmail(nino).value, 5.seconds)

      "get the email in the mongo database" in {
        inSequence {
          mockFind(nino)(Future.successful(List(EmailData(nino, email), EmailData("not picked up", "not picked up"))))
          mockDecrypt(email)(Some(email))
        }

        get(nino)
      }

      "return a right if the get is successful" in {
        // try when there is an email first
        inSequence {
          mockFind(nino)(Future.successful(List(EmailData(nino, email))))
          mockDecrypt(email)(Some(email))
        }
        get(nino) shouldBe Right(Some(email))

        // now try when there is no email
        mockFind(nino)(Future.successful(List()))
        get(nino) shouldBe Right(None)
      }

      "return a left if the get is unsuccessful" in {
        mockFind(nino)(Future.failed(new Exception("")))
        get(nino).isLeft shouldBe true

        inSequence {
          mockFind(nino)(Future.successful(List(EmailData(nino, email))))
          mockDecrypt(email)(None)
        }
        get(nino).isLeft shouldBe true
      }

    }

  }

}
