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

package uk.gov.hmrc.helptosave.controllers

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnector
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util.{Logging, NINO, LogMessageTransformer}
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps._

import scala.concurrent.Future

class EnrolmentStoreController @Inject() (val enrolmentStore: EnrolmentStore,
                                          val itmpConnector:  ITMPEnrolmentConnector,
                                          htsAuthConnector:   HtsAuthConnector)(
    implicit
    transformer: LogMessageTransformer
)
  extends HelpToSaveAuth(htsAuthConnector) with Logging with WithMdcExecutionContext with EnrolmentBehaviour {

  import EnrolmentStoreController._

  def enrol(): Action[AnyContent] = authorised { implicit request ⇒ implicit nino ⇒
    handle(enrolUser(nino), "enrol user", nino)
  }

  def setITMPFlag(): Action[AnyContent] = authorised { implicit request ⇒ implicit nino ⇒
    handle(setITMPFlagAndUpdateMongo(nino), "set ITMP flag", nino)
  }

  def getEnrolmentStatus(): Action[AnyContent] = authorised { implicit request ⇒ implicit nino ⇒
    handle(enrolmentStore.get(nino), "get enrolment status", nino)
  }

  private def handle[A](f: EitherT[Future, String, A], description: String, nino: NINO)(implicit hc: HeaderCarrier, writes: Writes[A]): Future[Result] = {
    val correlationId = hc.getCorrelationId
    f.fold(
      { e ⇒
        logger.warn(s"Could not $description: $e", nino, correlationId)
        InternalServerError
      }, { a ⇒
        logger.info(s"$description successful", nino, correlationId)
        Ok(Json.toJson(a))
      }
    )
  }

}

object EnrolmentStoreController {

  implicit val enrolmentStatusWrites: Writes[EnrolmentStore.Status] = new Writes[EnrolmentStore.Status] {
    case class EnrolmentStatusJSON(enrolled: Boolean, itmpHtSFlag: Boolean)

    implicit val enrolmentStatusWrites: Writes[EnrolmentStatusJSON] = Json.writes[EnrolmentStatusJSON]

    override def writes(o: EnrolmentStore.Status): JsValue = o match {
      case EnrolmentStore.Enrolled(itmpHtSFlag) ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = true, itmpHtSFlag = itmpHtSFlag))
      case EnrolmentStore.NotEnrolled           ⇒ Json.toJson(EnrolmentStatusJSON(enrolled    = false, itmpHtSFlag = false))
    }

  }

  implicit val unitWrites: Writes[Unit] = new Writes[Unit] {
    override def writes(o: Unit) = JsNull
  }

}
