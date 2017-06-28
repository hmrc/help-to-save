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

package uk.gov.hmrc.helptosave.connectors

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityCheckConnectorSpec extends TestSupport with WithFakeApplication {

  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  def mockGet(url: String)(response: HttpResponse) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, Map.empty[String, String], *, *)
      .returning(Future.successful(response))

  lazy val connector = new EligibilityCheckConnectorImpl {
    override val http = mockHttp
  }

  "check eligibility" must {
    val nino = randomNINO()

    val url = s"http://localhost:7002/help-to-save-stub/eligibilitycheck/$nino"

    "return true when the user is eligible" in {

      mockGet(url)(HttpResponse(200, Some(Json.toJson(EligibilityResult(true))))) // scalastyle:ignore magic.number

      Await.result(connector.isEligible(nino).value, 5.seconds) shouldBe Right(true)
    }

    "return false when the user is not eligible" in {

      mockGet(url)(HttpResponse(200, Some(Json.toJson(EligibilityResult(false))))) // scalastyle:ignore magic.number

      Await.result(connector.isEligible(nino).value, 5.seconds) shouldBe Right(false)
    }

    "handles errors parsing invalid json" in {

      mockGet(url)(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}""")))) // scalastyle:ignore magic.number

      Await.result(connector.isEligible(nino).value, 5.seconds).isLeft shouldBe true
    }
  }
}

