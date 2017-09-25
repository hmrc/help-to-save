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

import java.util.Base64

import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.helptosave.util.TryOps._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class EmailStoreController @Inject()(emailStore: EmailStore, htsAuthConnector: HtsAuthConnector)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(htsAuthConnector) with Logging {

  import uk.gov.hmrc.helptosave.controllers.EmailStoreController._

  val base64Decoder: Base64.Decoder = Base64.getDecoder()

  def store(email: String, nino: NINO): Action[AnyContent] = authorised { implicit request ⇒
    Try(new String(base64Decoder.decode(email))).fold(
      { error ⇒
        logger.warn(s"For NINO [$nino]: Could not store email. Could not decode email: $error")
        Future.successful(InternalServerError)
      }, { decodedEmail ⇒
        emailStore.storeConfirmedEmail(decodedEmail, nino).fold(
          { e ⇒
            logger.error(s"For NINO [$nino]: Could not store email: $e")
            InternalServerError
          }, { _ ⇒
            Ok
          }
        )
      }
    )
  }

  def get(nino: NINO): Action[AnyContent] =  authorised { implicit request ⇒
    emailStore.getConfirmedEmail(nino).fold(
      { e ⇒
        logger.warn(e)
        InternalServerError
      },
      maybeEmail ⇒ Ok(Json.toJson(EmailGetResponse(maybeEmail)))
    )
  }

}

object EmailStoreController {

  private[controllers] case class EmailGetResponse(email: Option[String])

  private[controllers] implicit val emailGetResponseFormat: Format[EmailGetResponse] = Json.format[EmailGetResponse]

}
