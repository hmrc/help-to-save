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
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, PayePersonalDetailsConnector}
import uk.gov.hmrc.helptosave.controllers.StrideController.PayeDetailsHolder
import uk.gov.hmrc.helptosave.models.{EligibilityResponseHolder, PayePersonalDetails}
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.helptosave.util.{Logging, NINOLogMessageTransformer, toFuture}

import scala.util.Try

class StrideController @Inject() (eligibilityCheckConnector:    EligibilityCheckConnector,
                                  payePersonalDetailsConnector: PayePersonalDetailsConnector,
                                  htsAuthConnector:             HtsAuthConnector)(implicit transformer: NINOLogMessageTransformer)

  extends StrideAuth(htsAuthConnector) with Logging with WithMdcExecutionContext {

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def eligibilityCheck(ninoParam: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒

    Try(new String(base64Decoder.decode(ninoParam))).fold(
      { error ⇒
        logger.warn(s"Could not decode nino from encrypted param: $error")
        InternalServerError
      }, { decodedNino ⇒
        eligibilityCheckConnector.isEligible(decodedNino).fold(
          {
            e ⇒
              logger.warn(s"Could not check eligibility: $e", decodedNino)
              InternalServerError
          }, {
            r ⇒
              Ok(Json.toJson(EligibilityResponseHolder(r)))
          }
        )
      }
    )
  }

  def getPayePersonalDetails(ninoParam: String): Action[AnyContent] = authorisedFromStride { implicit request ⇒

    Try(new String(base64Decoder.decode(ninoParam))).fold(
      { error ⇒
        logger.warn(s"Could not decode nino from encrypted param: $error")
        InternalServerError
      }, { decodedNino ⇒
        payePersonalDetailsConnector.getPersonalDetails(decodedNino)
          .fold(
            { error ⇒
              logger.warn(s"Could not retrieve paye-personal-details from DES: $error")
              InternalServerError
            }, {
              r ⇒
                Ok(Json.toJson(PayeDetailsHolder(r)))
            }
          )
      }
    )
  }
}

object StrideController {

  private case class PayeDetailsHolder(payeDetails: Option[PayePersonalDetails])

  private object PayeDetailsHolder {
    implicit val format: Format[PayeDetailsHolder] = Json.format[PayeDetailsHolder]
  }

}
