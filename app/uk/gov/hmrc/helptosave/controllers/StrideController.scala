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

import java.util.Base64

import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.connectors.PayePersonalDetailsConnector
import uk.gov.hmrc.helptosave.models.EligibilityResponseHolder
import uk.gov.hmrc.helptosave.services.EligibilityCheckService
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.helptosave.util.{Logging, NINO, LogMessageTransformer, toFuture}

import scala.concurrent.Future
import scala.util.Try

class StrideController @Inject() (eligibilityCheckService:      EligibilityCheckService,
                                  payePersonalDetailsConnector: PayePersonalDetailsConnector,
                                  htsAuthConnector:             HtsAuthConnector)(implicit transformer: LogMessageTransformer)

  extends StrideAuth(htsAuthConnector) with Logging with WithMdcExecutionContext {

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def eligibilityCheck(ninoParam: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒

    withBase64DecodedNINO(ninoParam) { decodedNino ⇒
      eligibilityCheckService.getEligibility(decodedNino).fold(
        {
          e ⇒
            logger.warn(s"Could not check eligibility: $e", decodedNino, None)
            InternalServerError
        }, {
          r ⇒
            Ok(Json.toJson(EligibilityResponseHolder(r)))
        }
      )
    }
  }

  def getPayePersonalDetails(ninoParam: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒

    withBase64DecodedNINO(ninoParam) { decodedNino ⇒
      payePersonalDetailsConnector.getPersonalDetails(decodedNino)
        .fold(
          { error ⇒
            logger.warn(s"Could not retrieve paye-personal-details from DES: $error")
            InternalServerError
          }, {
            r ⇒
              Ok(Json.toJson(r))
          }
        )
    }
  }

  private def withBase64DecodedNINO(ninoParam: String)(f: NINO ⇒ Future[Result])(implicit request: Request[AnyContent]): Future[Result] =
    Try(new String(base64Decoder.decode(ninoParam))).fold(
      { error ⇒
        logger.warn(s"Could not decode nino from encrypted param: $error")
        InternalServerError
      }, f
    )
}
