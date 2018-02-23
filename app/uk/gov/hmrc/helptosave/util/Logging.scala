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

package uk.gov.hmrc.helptosave.util

import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.http.HeaderCarrier

trait Logging {

  val logger: Logger = Logger(this.getClass)

}

object Logging {

  implicit class LoggerOps(val logger: Logger) {

    def debug(message: String, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.debug(transformer.transform(message, nino, getCorrelationId()))

    def info(message: String, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.info(transformer.transform(message, nino, getCorrelationId()))

    def warn(message: String, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.warn(transformer.transform(message, nino, getCorrelationId()))

    def warn(message: String, e: ⇒ Throwable, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.warn(transformer.transform(message, nino, getCorrelationId()), e)

    def error(message: String, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.error(transformer.transform(message, nino, getCorrelationId()))

    def error(message: String, e: ⇒ Throwable, nino: NINO)(implicit transformer: LogMessageTransformer, hc: HeaderCarrier): Unit =
      logger.error(transformer.transform(message, nino, getCorrelationId()), e)

    def getCorrelationId()(implicit hc: HeaderCarrier): Option[String] =
      hc.headers.find(p ⇒ p._1 === "X-CorrelationId").map(_._2)
  }

}

@ImplementedBy(classOf[LogMessageTransformerImpl])
trait LogMessageTransformer {
  def transform(message: String, nino: NINO, correlationId: Option[String] = None): String
}

@Singleton
class LogMessageTransformerImpl @Inject() (configuration: Configuration) extends LogMessageTransformer {

  private val ninoPrefix: NINO ⇒ String =
    if (configuration.underlying.getBoolean("nino-logging.enabled")) {
      nino ⇒ s"For NINO [$nino]: "
    } else {
      _ ⇒ ""
    }

  private val correlationIdPrefix: Option[String] ⇒ String =
    {
      case Some(id) ⇒ s", For CorrelationId $id"
      case None     ⇒ ""
    }

  def transform(message: String, nino: NINO, correlationId: Option[String]): String =
    ninoPrefix(nino) + correlationIdPrefix(correlationId) + message

}
