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

package uk.gov.hmrc.helptosave.modules

import java.util.Base64

import configs.syntax._
import com.google.inject.{AbstractModule, Inject}
import play.api.Configuration
import uk.gov.hmrc.helptosave.modules.EmailDeletionModule.EmailDeleter
import uk.gov.hmrc.helptosave.repo.EmailStore
import uk.gov.hmrc.helptosave.util.Logging

import scala.concurrent.ExecutionContext

class EmailDeletionModule extends AbstractModule {

  override def configure() = bind(classOf[EmailDeleter]).asEagerSingleton()

}

object EmailDeletionModule {

  class EmailDeleter @Inject() (config: Configuration, emailStore: EmailStore)(implicit ec: ExecutionContext) extends Logging {

    def base64Decode(s: String): String = new String(Base64.getDecoder.decode(s))

    val ninos: List[String] = config.underlying.get[List[String]]("email-deleter.ninos").value.map(base64Decode)

    if (ninos.nonEmpty) {
      logger.info(s"Deleting ${ninos.length} emails")
      ninos.foreach{ nino ⇒
        emailStore.deleteEmail(nino).value.onComplete{
          case scala.util.Success(value) ⇒
            value.fold(
              e ⇒ logger.warn(s"Could not delete email: $e"),
              _ ⇒ logger.info(s"Email successfully deleted")
            )

          case scala.util.Failure(exception) ⇒
            logger.warn(s"Could not delete email: ${exception.getMessage}")
        }

      }

    } else {
      logger.info("No emails to delete")
    }

  }

}

