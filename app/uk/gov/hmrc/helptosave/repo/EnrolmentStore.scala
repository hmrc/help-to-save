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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._
import play.api.Logging
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models.NINODeletionConfig
import uk.gov.hmrc.helptosave.models.account.AccountNumber
import uk.gov.hmrc.helptosave.models.enrolment.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.models.enrolment.EnrolmentData
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.util.Time.nanosToPrettyString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.SingleObservableFuture

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.scalaUtilChainingOps

@ImplementedBy(classOf[MongoEnrolmentStore])
trait EnrolmentStore {

  def get(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, Status]

  def updateDeleteFlag(
    ninosDeletionConfig: Seq[NINODeletionConfig],
    revertSoftDelete: Boolean = false
  ): EitherT[Future, String, Seq[NINODeletionConfig]]

  def updateItmpFlag(nino: NINO, itmpFlag: Boolean)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit]

  def updateWithAccountNumber(nino: NINO, accountNumber: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, String, Unit]

  def insert(
    nino: NINO,
    itmpFlag: Boolean,
    eligibilityReason: Option[Int],
    source: String,
    accountNumber: Option[String],
    deleteFlag: Option[Boolean]
  )(implicit hc: HeaderCarrier): EitherT[Future, String, Unit]

  def getAccountNumber(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, AccountNumber]
}

@Singleton
class MongoEnrolmentStore @Inject() (val mongo: MongoComponent, metrics: Metrics)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[EnrolmentData](
      mongoComponent = mongo,
      collectionName = "enrolments",
      domainFormat = EnrolmentData.ninoFormat,
      indexes = Seq(IndexModel(ascending("nino"), IndexOptions().name("ninoIndex")))
    )
    with EnrolmentStore
    with Logging {
  def getRegex(nino: String): String = "^" + nino.take(8) + ".$"

  private[repo] def doInsert(
    nino: NINO,
    eligibilityReason: Option[Int],
    source: String,
    itmpFlag: Boolean,
    accountNumber: Option[String],
    deleteFlag: Option[Boolean]
  )(implicit ec: ExecutionContext): Future[Unit] =
    preservingMdc {
      collection
        .insertOne(
          EnrolmentData(
            nino = nino,
            itmpHtSFlag = itmpFlag,
            eligibilityReason = eligibilityReason,
            source = Some(source),
            accountNumber = accountNumber,
            deleteFlag = deleteFlag
          )
        )
        .toFuture()
        .map(_ => ())
    }

  private[repo] def doUpdateItmpFlag(nino: NINO, itmpFlag: Boolean): Future[Option[EnrolmentData]] =
    preservingMdc {
      collection
        .findOneAndUpdate(
          filter = regex("nino", getRegex(nino)),
          update = Updates.set("itmpHtSFlag", itmpFlag),
          options = FindOneAndUpdateOptions().bypassDocumentValidation(false).returnDocument(ReturnDocument.AFTER)
        )
        .toFutureOption()
    }

  private[repo] def doUpdateDeleteFlag(enrolmentsToDelete: Seq[EnrolmentData], revertSoftDelete: Boolean = false)(
    implicit ec: ExecutionContext
  ): EitherT[Future, String, Seq[EnrolmentData]] = {

    val updateModels: Seq[UpdateOneModel[Nothing]] = enrolmentsToDelete.map { enrolment =>
      val filter =
        if !revertSoftDelete then regex("nino", getRegex(enrolment.nino))
        else {
          and(
            regex("nino", getRegex(enrolment.nino)),
            enrolment._id.fold(empty())(id => Filters.eq("_id", id)),
            Filters.eq("deleteFlag", true)
          )
        }

      UpdateOneModel(
        filter = filter,
        update =
          Updates.combine(Updates.set("deleteFlag", !revertSoftDelete), Updates.set("deleteDate", LocalDateTime.now())),
        updateOptions = UpdateOptions().bypassDocumentValidation(false)
      )
    }

    EitherT[Future, String, Unit] {
      preservingMdc {
        collection
          .bulkWrite(updateModels, BulkWriteOptions().ordered(false))
          .toFuture()
          .map { _ =>
            Right(())
          }
          .recover { case e =>
            Left(s"Failed to mark NINOs, ${enrolmentsToDelete.map(_.nino)}, as soft-delete : ${e.getMessage}")
          }
      }
    }.map(_ => enrolmentsToDelete)
  }

  private[repo] def persistAccountNumber(nino: NINO, accountNumber: String): Future[Option[EnrolmentData]] =
    preservingMdc {
      collection
        .findOneAndUpdate(
          filter = regex("nino", getRegex(nino)),
          update = Updates.set("accountNumber", accountNumber),
          options = FindOneAndUpdateOptions().bypassDocumentValidation(false).returnDocument(ReturnDocument.AFTER)
        )
        .toFutureOption()
    }

  override def get(nino: String)(implicit hc: HeaderCarrier): EitherT[Future, String, Status] =
    EitherT[Future, String, Status] {
      preservingMdc {
        val timerContext = metrics.enrolmentStoreGetTimer.time()

        collection
          .find(
            and(
              regex("nino", getRegex(nino)),
              or(
                exists("deleteFlag", exists = false),
                Filters.eq("deleteFlag", false)
              )
            )
          )
          .toFuture()
          .map { res =>
            timerContext.stop()

            Right(res.headOption.fold[Status](NotEnrolled)(data => Enrolled(data.itmpHtSFlag)))
          }
          .recover { case e =>
            timerContext.stop()
            metrics.enrolmentStoreGetErrorCounter.inc()

            Left(s"For NINO [$nino]: Could not read from enrolment store: ${e.getMessage}")
          }
      }
    }

  override def updateItmpFlag(nino: NINO, itmpFlag: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, String, Unit] =
    EitherT {
      val timerContext = metrics.enrolmentStoreUpdateTimer.time()

      doUpdateItmpFlag(nino, itmpFlag)
        .map[Either[String, Unit]] { result =>
          val time = timerContext.stop()

          result.fold[Either[String, Unit]] {
            metrics.enrolmentStoreUpdateErrorCounter.inc()
            Left(s"For NINO [$nino]: Could not update enrolment store (round-trip time: ${nanosToPrettyString(time)})")
          } { _ =>
            Right(())
          }
        }
        .recover { case e =>
          timerContext.stop()
          metrics.enrolmentStoreUpdateErrorCounter.inc()

          Left(s"Failed to write to enrolments store: ${e.getMessage}")
        }
    }

  override def updateDeleteFlag(
    ninosDeletionConfig: Seq[NINODeletionConfig],
    revertSoftDelete: Boolean
  ): EitherT[Future, String, Seq[NINODeletionConfig]] = {
    val timerContext = metrics.enrolmentStoreGetTimer.time()

    val filter = or(
      ninosDeletionConfig.map { config =>
        and(
          regex("nino", getRegex(config.nino)),
          config.docID.fold(empty())(id => Filters.eq("_id", id))
        )
      }*
    )

    preservingMdc {
      collection
        .find(filter)
        .toFuture()
        .map { availableNINOs =>
          val missingNINOs = ninosDeletionConfig.map(_.nino).diff(availableNINOs.map(_.nino).distinct)

          missingNINOs match {
            case Seq() => Right(availableNINOs)
            case _     =>
              metrics.enrolmentStoreDeleteErrorCounter(revertSoftDelete).inc()
              Left(s"Following requested NINOs not found in system : $missingNINOs")
          }
        }
    }.recover { case e =>
      timerContext.stop()
      metrics.enrolmentStoreDeleteErrorCounter(revertSoftDelete).inc()
      Left(s"Search for NINOs failed: ${e.getMessage}")
    }.pipe(EitherT(_))
      .flatMap(doUpdateDeleteFlag(_, revertSoftDelete))
      .map(_.map(enrolment => NINODeletionConfig(enrolment.nino, enrolment._id)))
  }

  override def updateWithAccountNumber(nino: NINO, accountNumber: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, String, Unit] =
    EitherT {
      val timerContext = metrics.enrolmentStoreUpdateTimer.time()

      persistAccountNumber(nino, accountNumber)
        .map[Either[String, Unit]] { result =>
          val time = timerContext.stop()

          result.fold[Either[String, Unit]] {
            metrics.enrolmentStoreUpdateErrorCounter.inc()
            Left(
              s"For NINO [$nino]: Could not update enrolment store with account number (round-trip time: ${nanosToPrettyString(time)})"
            )
          } { _ =>
            Right(())
          }
        }
        .recover { case e =>
          timerContext.stop()
          metrics.enrolmentStoreUpdateErrorCounter.inc()

          Left(s"Failed to write to enrolments store: ${e.getMessage}")
        }
    }

  override def insert(
    nino: NINO,
    itmpFlag: Boolean,
    eligibilityReason: Option[Int],
    source: String,
    accountNumber: Option[String],
    deleteFlag: Option[Boolean]
  )(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] =
    EitherT(
      doInsert(nino, eligibilityReason, source, itmpFlag, accountNumber, deleteFlag)
        .map[Either[String, Unit]] { _ =>
          Right(())
        }
        .recover { case e =>
          Left(e.getMessage)
        }
    )

  override def getAccountNumber(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, AccountNumber] =
    EitherT {
      val timerContext = metrics.enrolmentStoreGetTimer.time()

      preservingMdc {
        collection
          .find(regex("nino", getRegex(nino)))
          .toFuture()
          .map[Either[String, AccountNumber]] { res =>
            timerContext.stop()

            Right(AccountNumber(res.headOption.flatMap(_.accountNumber)))
          }
      }.recover { case e =>
        timerContext.stop()
        metrics.enrolmentStoreGetErrorCounter.inc()

        Left(s"For NINO [$nino]: Could not read account number from enrolment store: ${e.getMessage}")
      }
    }
}
