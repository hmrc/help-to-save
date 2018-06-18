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

import java.util.UUID

import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import play.api.libs.json.{Json, Writes}
import play.api.mvc._
import uk.gov.hmrc.helptosave.util
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging}

case class NsiAccountQueryParams(nino: String, systemId: String, correlationId: String)

trait AccountQuery extends Logging with Results {
  this: HelpToSaveAuth ⇒

  /**
   * Behaviour common to actions that query for Help to Save account data based on NINO
   */
  protected def accountQuery[A](nino:          String,
                                systemId:      String,
                                correlationId: Option[String])(query: Request[AnyContent] ⇒ NsiAccountQueryParams ⇒ util.Result[Option[A]])(implicit transformer: LogMessageTransformer, writes: Writes[A]): Action[AnyContent] =
    ggAuthorisedWithNino { implicit request ⇒ implicit authNino ⇒
      if (!uk.gov.hmrc.domain.Nino.isValid(nino)) {
        logger.warn("NINO in request was not valid")
        BadRequest
      } else if (nino =!= authNino) {
        logger.warn("NINO in request did not match NINO found in auth")
        Forbidden
      } else {
        val id = correlationId.getOrElse(UUID.randomUUID().toString)
        query(request)(NsiAccountQueryParams(nino, systemId, id))
          .fold(
            { errorString ⇒
              logger.warn(errorString, nino, "correlationId" → id)
              InternalServerError
            },
            _.fold[Result](NotFound)(found ⇒ Ok(Json.toJson(found)))
          )
      }
    }
}
