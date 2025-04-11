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

import org.bson.types.ObjectId
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, Json, __}
import uk.gov.hmrc.helptosave.models.NINODeletionConfig
import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.CollectionFactory
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits.objectIdFormat
import uk.gov.hmrc.mongo.test.MongoSupport
import org.mongodb.scala.ObservableFuture

import scala.concurrent.Await
import scala.concurrent.duration._

class MongoEnrolmentStoreSpec extends TestSupport with MongoSupport with BeforeAndAfterEach {

  val repository: MongoEnrolmentStore = fakeApplication.injector.instanceOf[MongoEnrolmentStore]
  override def beforeEach(): Unit =
    await(repository.collection.drop().toFuture())

  val ninoDifferentSuffix = "AE123456B"

  val accountNumber = "1234567890"

  def newMongoEnrolmentStore(mongoComponent: MongoComponent) =
    new MongoEnrolmentStore(mongoComponent, mockMetrics)

  private val duration: FiniteDuration = 15.seconds

  def create(
    nino: NINO,
    itmpNeedsUpdate: Boolean,
    eligibilityReason: Option[Int],
    channel: String,
    store: MongoEnrolmentStore,
    accountNumber: Option[String],
    deleteFlag: Option[Boolean]): Either[String, Unit] =
    Await.result(
      store.insert(nino, itmpNeedsUpdate, eligibilityReason, channel, accountNumber, deleteFlag).value,
      duration)

  def updateDeleteFlag(ninos: Seq[NINODeletionConfig], revertSoftDelete: Boolean, store: MongoEnrolmentStore): Either[String,Seq[NINODeletionConfig]] =
    Await.result(store.updateDeleteFlag(ninos, revertSoftDelete).value, duration)

  "The MongoEnrolmentStore" when {
    "creating" must {
      "create a new record in the db when inserted" in {
        val create1 = create(randomNINO(), itmpNeedsUpdate = true, Some(7), "online", repository, Some(accountNumber), None)
        create1 shouldBe Right(())
      }
    }

    def get(nino: NINO, store: MongoEnrolmentStore): Either[String, Status] =
      Await.result(store.get(nino).value, duration)

    "updating" must {

      def update(nino: NINO, itmpNeedsUpdate: Boolean, store: MongoEnrolmentStore): Either[String, Unit] =
        Await.result(store.updateItmpFlag(nino, itmpNeedsUpdate).value, 5.seconds)

      "update the mongodb collection" in {
        val nino = randomNINO()
        create(nino, itmpNeedsUpdate = false, Some(7), "online", repository, Some(accountNumber), None) shouldBe Right(())
        update(nino, itmpNeedsUpdate = true, repository) shouldBe Right(())
      }

      "return an error" when {

        "the future returned by mongo fails" in {
          update(randomNINO(), itmpNeedsUpdate = false, repository).isLeft shouldBe true
        }
      }

      "update the enrolment when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        create(nino, itmpNeedsUpdate = false, Some(7), "online", repository, Some(accountNumber), None) shouldBe Right(())
        update(ninoDifferentSuffix, itmpNeedsUpdate = true, repository) shouldBe Right(())
      }
    }

    "getting" must {

      def get(nino: NINO, store: MongoEnrolmentStore): Either[String, Status] =
        Await.result(store.get(nino).value, 50.seconds)

      "attempt to find the entry in the collection based on the input nino" in {
        val nino = randomNINO()
        get(nino, repository) shouldBe Right(NotEnrolled)
      }

      "return an enrolled status if an entry is found" in {
        val nino = randomNINO()
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), None) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }

      "return a not enrolled status if the entry is not found" in {
        val nino = randomNINO()
        get(nino, repository) shouldBe Right(NotEnrolled)
      }

      "return an enrolled status when a different nino suffix is used of an existing user" in {
        val nino = "AE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), None) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
        get(ninoDifferentSuffix, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }

      "return as not enrolled if the entry is marked as delete" in {
        val nino = "AE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), Some(true)) shouldBe Right(())
        get(nino, store) shouldBe Right(NotEnrolled)
      }

    }

    "soft-delete request " must {
      "update enrolment documents with delete_flag set to true and delete_date populated when valid NINOs given" in {
        val nino = "AE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), None) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))

        val ninosToDelete = Seq(NINODeletionConfig(nino, findDocumentId(nino, store)._2.headOption))

        updateDeleteFlag(ninosToDelete, revertSoftDelete = false, store) shouldBe Right(ninosToDelete)
        get(nino, store) shouldBe Right(NotEnrolled)
      }

      "not update enrolment documents when given NINOs are missing in system" in {
        val nino = randomNINO()
        updateDeleteFlag(Seq(NINODeletionConfig(nino)), revertSoftDelete = false, repository) shouldBe
          Left(s"Following requested NINOs not found in system : List($nino)")
      }

      "to undo the action, must set delete_flag to false if already marked as delete" in {
        val nino = "BE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), Some(true)) shouldBe Right(())
        get(nino, store) shouldBe Right(NotEnrolled)

        val ninosToDelete = Seq(NINODeletionConfig(nino, findDocumentId(nino, store)._2.headOption))

        updateDeleteFlag(ninosToDelete, revertSoftDelete = true, store) shouldBe Right(ninosToDelete)
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }

      "to undo the action, must be a no-op when enrolment is not marked for delete" in {
        val nino = "CE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), Some(false)) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))

        val ninosToDelete = Seq(NINODeletionConfig(nino, findDocumentId(nino, store)._2.headOption))

        updateDeleteFlag(ninosToDelete, revertSoftDelete = true, store) shouldBe Right(ninosToDelete)
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }

      "to undo the action, must be a no-op when enrolment has delete_flag missing" in {
        val nino = "DE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), None) shouldBe Right(())
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))

        val ninosToDelete = Seq(NINODeletionConfig(nino, findDocumentId(nino, store)._2.headOption))

        updateDeleteFlag(ninosToDelete, revertSoftDelete = true, store) shouldBe Right(ninosToDelete)
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }

      "to undo the action, must set delete_flag to false for the given object id, if already marked as delete" in {
        val nino = "EE123456A"
        val store = repository
        create(nino, itmpNeedsUpdate = true, Some(7), "online", store, Some(accountNumber), Some(true)) shouldBe Right(())
        create(nino, itmpNeedsUpdate = true, Some(3), "online", store, Some(accountNumber), Some(true)) shouldBe Right(())
        get(nino, store) shouldBe Right(NotEnrolled)

        val (collection: MongoCollection[EnrolmentDocId], docIdToRevertDeletion) = findDocumentId(nino, store)

        val toRevert = docIdToRevertDeletion.head

        val ninosToDelete = Seq(NINODeletionConfig(nino, Some(toRevert)))
        updateDeleteFlag(ninosToDelete, revertSoftDelete = true, store) shouldBe Right(ninosToDelete)

        // only above executed doc id should be marked as eligible and other one still with soft-delete
        val updatedDocs = await(collection.find(Filters.eq("nino", nino)).toFuture())(duration)
        val (reverted, deleted) = updatedDocs.partition(_._id == toRevert)

        reverted.head.deleteFlag shouldBe Some(false)
        deleted.head.deleteFlag shouldBe Some(true)
        get(nino, store) shouldBe Right(Enrolled(itmpHtSFlag = true))
      }
    }
  }

  private def findDocumentId(nino: NINO, store: MongoEnrolmentStore) = {
    val collection = CollectionFactory.collection(store.mongo.database, "enrolments", Json.format[EnrolmentDocId])
    val docIds = await(collection.find(Filters.eq("nino", nino)).map(_._id).toFuture())(duration)
    (collection, docIds)
  }

  case class EnrolmentDocId(_id: ObjectId, nino: NINO, deleteFlag: Option[Boolean])

  object EnrolmentDocId {
    implicit val format: Format[EnrolmentDocId] = {
      ((__ \ "_id").format[ObjectId] and (__ \ "nino").format[String] and (__ \ "deleteFlag").formatNullable[Boolean])(
        EnrolmentDocId.apply,
        doc => (doc._id, doc.nino, doc.deleteFlag)
      )
    }
  }

}
