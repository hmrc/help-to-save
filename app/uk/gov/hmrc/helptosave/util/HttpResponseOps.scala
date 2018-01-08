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

import play.api.libs.json.{JsError, Reads}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.TryOps._
import uk.gov.hmrc.http.HttpResponse

import scala.util.Try

object HttpResponseOps {

  implicit def httpResponseOps(response: HttpResponse): HttpResponseOps = new HttpResponseOps(response)

}

class HttpResponseOps(val response: HttpResponse) extends AnyVal {

  def parseJson[A](implicit reads: Reads[A]): Either[String, A] =
    Try(response.json).fold(
      error ⇒
        // response.json failed in this case - there was no JSON in the response
        Left(s"Could not read http response as JSON (${error.getMessage})."),
      jsValue ⇒
        // use Option here to filter out null values
        Option(jsValue).fold[Either[String, A]](
          Left("No JSON found in body of http response")
        )(_.validate[A].fold[Either[String, A]](
            errors ⇒
              // there was JSON in the response but we couldn't read it
              Left(s"Could not parse http reponse JSON: ${JsError(errors).prettyPrint()}."),
            Right(_)
          )
          )
    )

}
