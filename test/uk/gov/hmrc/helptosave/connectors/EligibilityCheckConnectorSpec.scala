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
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.EligibilityCheckResult
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EligibilityCheckConnectorSpec extends TestSupport with GeneratorDrivenPropertyChecks with ServicesConfig {

  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  def url(nino: String) = {
    val itmpBaseURL: String = baseUrl("itmp-eligibility-check")
    s"$itmpBaseURL/help-to-save/eligibility-check/$nino"
  }

  def mockGet(url: String)(response: HttpResponse) =
    (mockHttp.get(_: String)(_: HeaderCarrier))
      .expects(url, *)
      .returning(Future.successful(response))

  lazy val connector = new EligibilityCheckConnectorImpl(mockHttp, mockMetrics)

  implicit val resultArb: Arbitrary[EligibilityCheckResult] = Arbitrary(for {
    result ← Gen.choose(1, 2)
    reason ← Gen.choose(1, 8)
  } yield EligibilityCheckResult(result, reason))

  "check eligibility" must {
    val nino = randomNINO()

    "return with the eligibility check result unchanged from ITMP" in {
      forAll { result: EligibilityCheckResult ⇒
        mockGet(url(nino))(HttpResponse(200, Some(Json.toJson(result)))) // scalastyle:ignore magic.number
        Await.result(connector.isEligible(nino).value, 5.seconds) shouldBe Right(result)
      }

    }

    "handles errors parsing invalid json" in {
      mockGet(url(nino))(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}""")))) // scalastyle:ignore magic.number

      Await.result(connector.isEligible(nino).value, 5.seconds).isLeft shouldBe true
    }
  }
}

