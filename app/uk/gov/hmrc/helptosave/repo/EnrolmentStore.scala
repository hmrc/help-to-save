/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models.account.AccountNumber
import uk.gov.hmrc.helptosave.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosave.util.Time.nanosToPrettyString
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEnrolmentStore])
trait EnrolmentStore {

  import EnrolmentStore._

  def get(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, Status]

  def updateItmpFlag(nino: NINO, itmpFlag: Boolean)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit]

  def updateWithAccountNumber(nino: NINO, accountNumber: String)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit]

  def insert(nino: NINO, itmpFlag: Boolean, eligibilityReason: Option[Int], source: String, accountNumber: Option[String])(implicit hc: HeaderCarrier): EitherT[Future, String, Unit]

  def getAccountNumber(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, AccountNumber]

}

object EnrolmentStore {

  sealed trait Status

  case class Enrolled(itmpHtSFlag: Boolean) extends Status

  case object NotEnrolled extends Status

  object Status {

    private case class EnrolmentStatusJSON(enrolled: Boolean, itmpHtSFlag: Boolean)

    private implicit val enrolmentStatusJSONFormat: Format[EnrolmentStatusJSON] = Json.format[EnrolmentStatusJSON]

    implicit val enrolmentStatusFormat: Format[Status] = new Format[Status] {

      override def writes(o: Status): JsValue = o match {
        case EnrolmentStore.Enrolled(itmpHtSFlag) ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = true, itmpHtSFlag = itmpHtSFlag))
        case EnrolmentStore.NotEnrolled           ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = false, itmpHtSFlag = false))
      }

      override def reads(json: JsValue): JsResult[Status] = json.validate[EnrolmentStatusJSON].map {
        case EnrolmentStatusJSON(true, flag) ⇒ Enrolled(flag)
        case EnrolmentStatusJSON(false, _)   ⇒ NotEnrolled
      }
    }

  }

}

@Singleton
class MongoEnrolmentStore @Inject() (mongo:   ReactiveMongoComponent,
                                     metrics: Metrics)(implicit ec: ExecutionContext, transformer: LogMessageTransformer)
  extends ReactiveRepository[EnrolmentData, BSONObjectID](
    collectionName = "enrolments",
    mongo          = mongo.mongoConnector.db,
    EnrolmentData.ninoFormat,
    ReactiveMongoFormats.objectIdFormats)
  with EnrolmentStore {

  val log: Logger = new Logger(logger)

  def getRegex(nino: String): String = "^" + nino.take(8) + ".$"

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  private[repo] def doInsert(nino:              NINO,
                             eligibilityReason: Option[Int],
                             source:            String,
                             itmpFlag:          Boolean,
                             accountNumber:     Option[String])(implicit ec: ExecutionContext): Future[WriteResult] = {
    accountNumber match {
      case Some(accountNum) ⇒ collection.insert(BSONDocument("nino" -> nino, "itmpHtSFlag" -> itmpFlag,
        "eligibilityReason" -> eligibilityReason, "source" -> source, "accountNumber" -> accountNum))

      case None ⇒ collection.insert(BSONDocument("nino" -> nino, "itmpHtSFlag" -> itmpFlag,
        "eligibilityReason" -> eligibilityReason, "source" -> source))
    }
  }

  private[repo] def doUpdateItmpFlag(nino: NINO, itmpFlag: Boolean)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
    collection.findAndUpdate(
      BSONDocument("nino" -> BSONDocument("$regex" -> getRegex(nino))),
      BSONDocument("$set" -> BSONDocument("itmpHtSFlag" -> itmpFlag)),
      fetchNewObject = true,
      upsert         = false
    ).map(_.result[EnrolmentData])

  private[repo] def persistAccountNumber(nino: NINO, accountNumber: String)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
    collection.findAndUpdate(
      BSONDocument("nino" -> BSONDocument("$regex" -> getRegex(nino))),
      BSONDocument("$set" -> BSONDocument("accountNumber" -> accountNumber)),
      fetchNewObject = true,
      upsert         = false
    ).map(_.result[EnrolmentData])

  override def get(nino: String)(implicit hc: HeaderCarrier): EitherT[Future, String, EnrolmentStore.Status] =
    EitherT[Future, String, EnrolmentStore.Status](
      {
        val timerContext = metrics.enrolmentStoreGetTimer.time()

        find("nino" → Json.obj("$regex" → JsString(getRegex(nino)))).map { res ⇒
          val time = timerContext.stop()

          Right(res.headOption.fold[Status](NotEnrolled)(data ⇒ Enrolled(data.itmpHtSFlag)))
        }.recover {
          case e ⇒
            val time = timerContext.stop()
            metrics.enrolmentStoreGetErrorCounter.inc()

            Left(s"For NINO [$nino]: Could not read from enrolment store: ${e.getMessage}")
        }
      })

  override def updateItmpFlag(nino: NINO, itmpFlag: Boolean)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] = {
    EitherT({
      val timerContext = metrics.enrolmentStoreUpdateTimer.time()

      doUpdateItmpFlag(nino, itmpFlag).map[Either[String, Unit]] { result ⇒
        val time = timerContext.stop()

        result.fold[Either[String, Unit]] {
          metrics.enrolmentStoreUpdateErrorCounter.inc()
          Left(s"For NINO [$nino]: Could not update enrolment store (round-trip time: ${nanosToPrettyString(time)})")
        } { _ ⇒
          Right(())
        }
      }.recover {
        case e ⇒
          val time = timerContext.stop()
          metrics.enrolmentStoreUpdateErrorCounter.inc()

          Left(s"Failed to write to enrolments store: ${e.getMessage}")
      }
    }
    )
  }

  override def updateWithAccountNumber(nino: NINO, accountNumber: String)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] = {
    EitherT({
      val timerContext = metrics.enrolmentStoreUpdateTimer.time()

      persistAccountNumber(nino, accountNumber).map[Either[String, Unit]] { result ⇒
        val time = timerContext.stop()

        result.fold[Either[String, Unit]] {
          metrics.enrolmentStoreUpdateErrorCounter.inc()
          Left(s"For NINO [$nino]: Could not update enrolment store with account number (round-trip time: ${nanosToPrettyString(time)})")
        } { _ ⇒
          Right(())
        }
      }.recover {
        case e ⇒
          val time = timerContext.stop()
          metrics.enrolmentStoreUpdateErrorCounter.inc()

          Left(s"Failed to write to enrolments store: ${e.getMessage}")
      }
    }
    )
  }

  override def insert(nino:              NINO,
                      itmpFlag:          Boolean,
                      eligibilityReason: Option[Int],
                      source:            String,
                      accountNumber:     Option[String])(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] =
    EitherT(
      doInsert(nino, eligibilityReason, source, itmpFlag, accountNumber)
        .map[Either[String, Unit]] { writeResult ⇒
          if (writeResult.writeErrors.nonEmpty) {
            Left(writeResult.writeErrors.map(_.errmsg).mkString(","))
          } else {
            Right(())
          }
        }.recover {
          case e ⇒
            Left(e.getMessage)
        }
    )

  override def getAccountNumber(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, AccountNumber] =
    EitherT(
      {
        val timerContext = metrics.enrolmentStoreGetTimer.time()

        find("nino" → Json.obj("$regex" → JsString(getRegex(nino)))).map[Either[String, AccountNumber]] { res ⇒
          val time = timerContext.stop()

          //Right(res.headOption.fold[Status](NotEnrolled)(data ⇒ Enrolled(data.itmpHtSFlag)))

          Right(AccountNumber(res.headOption.flatMap(_.accountNumber)))
        }.recover {
          case e ⇒
            val time = timerContext.stop()
            metrics.enrolmentStoreGetErrorCounter.inc()

            Left(s"For NINO [$nino]: Could not read account number from enrolment store: ${e.getMessage}")
        }
      })
}

object MongoEnrolmentStore {

  private[repo] case class EnrolmentData(nino:              String,
                                         itmpHtSFlag:       Boolean,
                                         eligibilityReason: Option[Int]    = None,
                                         source:            Option[String] = None,
                                         accountNumber:     Option[String] = None)

  private[repo] object EnrolmentData {
    implicit val ninoFormat: Format[EnrolmentData] = Json.format[EnrolmentData]
  }

}
