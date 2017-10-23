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

import play.api.Logger

trait Logging {

  val logger: Logger = Logger(this.getClass)

}

object Logging {

  private def loggingPrefix(nino: String): String = s"For NINO [$nino]: "

  implicit class LoggerOps(val logger: Logger) {

    def debug(message: String, nino: NINO): Unit = logger.debug(loggingPrefix(nino) + message)

    def info(message: String, nino: NINO): Unit = logger.info(loggingPrefix(nino) + message)

    def warn(message: String, nino: NINO): Unit = logger.warn(loggingPrefix(nino) + message)

    def warn(message: String, e: ⇒ Throwable, nino: NINO): Unit = logger.warn(loggingPrefix(nino) + message, e)

    def error(message: String, nino: NINO): Unit = logger.error(loggingPrefix(nino) + message)

    def error(message: String, e: ⇒ Throwable, nino: NINO): Unit = logger.error(loggingPrefix(nino) + message, e)

  }
}
