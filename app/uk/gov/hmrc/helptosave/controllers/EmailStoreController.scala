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
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.util.{Logging, NINO}
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class EmailStoreController @Inject() (emailStore: EmailStore)(implicit ec: ExecutionContext)
  extends BaseController with Logging {

  val decoder: Base64.Decoder = Base64.getDecoder

  def store(email: String, nino: NINO): Action[AnyContent] = Action.async { implicit request ⇒
    Try(new String(decoder.decode(email))).fold(
      { error ⇒
        logger.warn(s"Could not store email for $nino. Could not decode email: $error")
        Future.successful(InternalServerError)
      }, { decodedEmail ⇒
        emailStore.storeConfirmedEmail(decodedEmail, nino).fold(
          { e ⇒
            logger.error(s"Could not store email for user $nino: $e")
            InternalServerError
          }, { _ ⇒
            Ok
          }
        )
      }
    )

  }

}
