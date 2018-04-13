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

package uk.gov.hmrc.helptosave.connectors

import cats.instances.int._
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class PayePersonalDetailsConnectorSpec
  extends TestSupport
  with GeneratorDrivenPropertyChecks
  with MockPagerDuty
  with TestData {

  lazy val connector = new PayePersonalDetailsConnectorImpl(mockHttp, mockMetrics, mockPagerDuty)

  def mockGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, connector.ppdHeaders, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  "PayePersonalDetailsConnector" must {

    val nino = randomNINO()

    "return pay personal details for a successful nino" in {

      val url = connector.payePersonalDetailsUrl(nino)

      mockGet(url)(Some(HttpResponse(200, Some(Json.parse(payeDetails(nino)))))) // scalastyle:ignore magic.number

      Await.result(connector.getPersonalDetails(nino).value, 5.seconds) shouldBe Right(ppDetails)
    }

    "handle 404 resposne when a nino is not found in DES" in {

      val url = connector.payePersonalDetailsUrl(nino)
      mockGet(url)(Some(HttpResponse(404, None))) // scalastyle:ignore magic.number
      mockPagerDutyAlert("Received unexpected http status in response to paye-personal-details")
      Await.result(connector.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
    }

    "handle errors when parsing invalid json" in {
      inSequence {
        val url = connector.payePersonalDetailsUrl(nino)
        mockGet(url)(Some(HttpResponse(200, Some(Json.toJson("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
        // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
      }
      Await.result(connector.getPersonalDetails(nino).value, 15.seconds).isLeft shouldBe true
    }

    "return with an error" when {
      "the call fails" in {
        inSequence {
          mockGet(connector.payePersonalDetailsUrl(nino))(None)
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to paye-personal-details")
        }

        Await.result(connector.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
      }

      "the call comes back with an unexpected http status" in {
        forAll { status: Int ⇒
          whenever(status > 0 && status =!= 200 && status =!= 404) {
            inSequence {
              mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(status)))
              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Received unexpected http status in response to paye-personal-details")
            }

            Await.result(connector.getPersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
          }

        }

      }
    }
  }
}
