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

package uk.gov.hmrc.helptosave.controllers

import cats.data.EitherT
import cats.instances.future._

import com.google.inject.Inject
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnector
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreController @Inject() (enrolmentStore: EnrolmentStore, itmpConnector: ITMPEnrolmentConnector)(implicit ec: ExecutionContext)
  extends BaseController with Logging {

  import EnrolmentStoreController._

  def enrol(nino: NINO): Action[AnyContent] = Action.async{ implicit request ⇒
    handle(
      for {
        _ ← enrolmentStore.update(nino, itmpFlag = false)
        _ ← setITMPFlagAndUpdateMongo(nino)
      } yield (),
      "enrol user",
      nino
    )
  }

  def setITMPFlag(nino: NINO): Action[AnyContent] = Action.async{ implicit request ⇒
    handle(setITMPFlagAndUpdateMongo(nino), "set ITMP flag", nino)
  }

  def getEnrolmentStatus(nino: NINO): Action[AnyContent] = Action.async{ implicit request ⇒
    handle(enrolmentStore.get(nino), "get enrolment status", nino)
  }

  private def handle[A](f: EitherT[Future, String, A], description: String, nino: NINO)(implicit writes: Writes[A]): Future[Result] =
    f.fold(
      { e ⇒
        logger.warn(s"Could not $description for $nino: $e")
        InternalServerError
      }, { a ⇒
        logger.info(s"$description successful for $nino")
        Ok(Json.toJson(a))
      }
    )

  private def setITMPFlagAndUpdateMongo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = for {
    _ ← itmpConnector.setFlag(nino)(hc, ec)
    _ ← enrolmentStore.update(nino, itmpFlag = true)
  } yield ()

}

object EnrolmentStoreController {

  implicit val enrolmentStatusWrites: Writes[EnrolmentStore.Status] = new Writes[EnrolmentStore.Status] {
    case class EnrolmentStatusJSON(enrolled: Boolean, itmpHtSFlag: Boolean)

    implicit val enrolmentStatusWrites: Writes[EnrolmentStatusJSON] = Json.writes[EnrolmentStatusJSON]

    override def writes(o: EnrolmentStore.Status) = o match {
      case EnrolmentStore.Enrolled(itmpHtSFlag) ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = true, itmpHtSFlag = itmpHtSFlag))
      case EnrolmentStore.NotEnrolled           ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = false, itmpHtSFlag = false))
    }

  }

  implicit val unitWrites: Writes[Unit] = new Writes[Unit] {
    override def writes(o: Unit) = JsNull
  }

}
