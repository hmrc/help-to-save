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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnector
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps.getApiCorrelationId
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, NINO}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EnrolmentStoreController @Inject() (val enrolmentStore: EnrolmentStore,
                                          val itmpConnector:  ITMPEnrolmentConnector,
                                          authConnector:      AuthConnector)(
    implicit
    transformer: LogMessageTransformer, appConfig: AppConfig)
  extends HelpToSaveAuth(authConnector) with EnrolmentBehaviour {

  import EnrolmentStoreController._

  implicit val correlationIdHeaderName: String = appConfig.correlationIdHeaderName

  def setITMPFlag(): Action[AnyContent] = ggAuthorisedWithNino { implicit request ⇒ implicit nino ⇒
    handle(setITMPFlagAndUpdateMongo(nino), "set ITMP flag", nino)
  }

  def getEnrolmentStatus(): Action[AnyContent] = ggAuthorisedWithNino { implicit request ⇒ implicit nino ⇒
    handle(enrolmentStore.get(nino), "get enrolment status", nino)
  }

  private def handle[A](f: EitherT[Future, String, A], description: String, nino: NINO)(implicit hc: HeaderCarrier, writes: Writes[A]): Future[Result] = {
    val additionalParams = "apiCorrelationId" -> getApiCorrelationId
    f.fold(
      { e ⇒
        logger.warn(s"Could not $description: $e", nino, additionalParams)
        InternalServerError
      }, { a ⇒
        logger.info(s"$description successful", nino, additionalParams)
        Ok(Json.toJson(a))
      }
    )
  }

}

object EnrolmentStoreController {

  implicit val unitWrites: Writes[Unit] = new Writes[Unit] {
    override def writes(o: Unit) = JsNull
  }

}
