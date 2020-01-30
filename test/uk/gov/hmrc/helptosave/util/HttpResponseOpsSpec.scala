/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.HttpResponse

import scala.util.Right

class HttpResponseOpsSpec extends TestSupport {

  case class Test1(a: Int)
  case class Test2(b: String)

  implicit val test1Format: Format[Test1] = Json.format[Test1]
  implicit val test2Format: Format[Test2] = Json.format[Test2]

  case class ThrowingHttpResponse() extends HttpResponse {
    override def json: JsValue = sys.error("")
    override def body: String = ""
    override def status: Int = 0
  }

  "HttpResponseOps" must {

    "provide a method to parse JSON" in {
      val status = 200
      val data = Test1(0)

      // test when there is an exception
      ThrowingHttpResponse().parseJson[Test1].isLeft shouldBe true

      // test when there is no JSON
      HttpResponse(status).parseJson[Test1].isLeft shouldBe true

      // test when the JSON isn't the right format
      HttpResponse(status, Some(Json.toJson(data))).parseJson[Test2].isLeft shouldBe true

      // test when everything is ok
      HttpResponse(status, Some(Json.toJson(data))).parseJson[Test1] shouldBe Right(data)
    }
  }
}
