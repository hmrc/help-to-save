/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{JsError, Reads}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.http.HttpResponse

import scala.language.implicitConversions
import scala.util.Try

object HttpResponseOps {

  implicit def httpResponseOps(response: HttpResponse): HttpResponseOps = new HttpResponseOps(response)

}

class HttpResponseOps(val response: HttpResponse) extends AnyVal {

  def parseJson[A](implicit reads: Reads[A]): Either[String, A] =
    parseJsonImpl(
      couldntReadJson = (response, ex: Throwable) =>
        s"Could not read http response as JSON (${ex.getMessage}). Response body was ${maskNino(response.body)}",
      couldntParseJson = (response, e: JsError) =>
        s"Could not parse http response JSON: ${e.prettyPrint()}. Response body was ${maskNino(response.body)}"
    )

  def parseJsonWithoutLoggingBody[A](implicit reads: Reads[A]): Either[String, A] =
    parseJsonImpl(
      couldntReadJson = (_, ex: Throwable) => s"Could not read http response as JSON (${ex.getMessage})",
      couldntParseJson = (_, e: JsError) => s"Could not parse http response JSON: ${e.prettyPrint()}"
    )

  def correlationId: String = response.header("CorrelationId").getOrElse("-")

  private def parseJsonImpl[A](
    couldntReadJson: (HttpResponse, Throwable) => String,
    couldntParseJson: (HttpResponse, JsError) => String
  )(implicit reads: Reads[A]): Either[String, A] =
    for
      jsValue <- Try(response.json).toEither.left.map(ex => couldntReadJson(response, ex))
      result  <- jsValue.validate[A].asEither.left.map(a => couldntParseJson(response, JsError(a)))
    yield result
}
