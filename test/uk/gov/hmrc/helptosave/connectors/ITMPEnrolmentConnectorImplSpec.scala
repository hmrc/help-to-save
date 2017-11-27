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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsNull, Writes}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class ITMPEnrolmentConnectorImplSpec extends TestSupport with GeneratorDrivenPropertyChecks with MockPagerDuty {

  lazy val connector = new ITMPEnrolmentConnectorImpl(mockHttp, mockMetrics, mockPagerDuty)

  def mockPut[A](url: String, body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp.put(_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, connector.desHeaders, *, *, *)
      .returning(result.fold[Future[HttpResponse]](Future.failed(new Exception("")))(Future.successful))

  "The ITMPConnectorImpl" when {

    val nino = "NINO"

      def url(nino: NINO): String = s"${connector.itmpEnrolmentURL}/help-to-save/accounts/$nino"

    "setting the ITMP flag" must {

      "perform a post to the configured URL" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(200)))

        await(connector.setFlag(nino).value)
      }

      "return a Right if the call to ITMP comes back with a 200 (OK) status" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(200)))

        await(connector.setFlag(nino).value) shouldBe Right(())
      }

      "return a Right if the call to ITMP comes back with a 403 (FORBIDDEN) status" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(403)))

        await(connector.setFlag(nino).value) shouldBe Right(())
      }

      "return a Left" when {

        "the call to ITMP comes back with a status which isn't 200 or 403" in {
          forAll{ status: Int ⇒
            whenever(status != 200 && status != 403){
              inSequence{
                mockPut(url(nino), JsNull)(Some(HttpResponse(status)))
                mockPagerDutyAlert("Received unexpected http status in response to setting ITMP flag")
              }

              await(connector.setFlag(nino).value).isLeft shouldBe true
            }
          }
        }

        "an error occurs while calling the ITMP endpoint" in {
          inSequence {
            mockPut(url(nino), JsNull)(None)
            mockPagerDutyAlert("Failed to make call to set ITMP flag")
          }
          await(connector.setFlag(nino).value).isLeft shouldBe true
        }

      }

    }

  }
}
