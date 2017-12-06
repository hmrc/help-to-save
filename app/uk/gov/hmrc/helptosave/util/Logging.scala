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

package uk.gov.hmrc.helptosave.util

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.{Configuration, Logger}

trait Logging {

  val logger: Logger = Logger(this.getClass)

}

object Logging {

  implicit class LoggerOps(val logger: Logger) {

    def debug(message: String, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.debug(transformer.transform(message, nino))

    def info(message: String, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.info(transformer.transform(message, nino))

    def warn(message: String, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.warn(transformer.transform(message, nino))

    def warn(message: String, e: ⇒ Throwable, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.warn(transformer.transform(message, nino), e)

    def error(message: String, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.error(transformer.transform(message, nino))

    def error(message: String, e: ⇒ Throwable, nino: NINO)(implicit transformer: NINOLogMessageTransformer): Unit =
      logger.error(transformer.transform(message, nino), e)

  }
}

@ImplementedBy(classOf[NINOLogMessageTransformerImpl])
trait NINOLogMessageTransformer {
  def transform(message: String, nino: NINO): String
}

@Singleton
class NINOLogMessageTransformerImpl @Inject() (configuration: Configuration) extends NINOLogMessageTransformer {

  private val loggingPrefix: NINO ⇒ String =
    if (configuration.underlying.getBoolean("nino-logging.enabled")) {
      nino ⇒ s"For NINO [$nino]: "
    } else {
      _ ⇒ ""
    }

  def transform(message: String, nino: NINO): String = loggingPrefix(nino) + message

}
