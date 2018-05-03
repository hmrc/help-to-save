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

import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ThresholdConnectorSpec extends TestSupport with MockPagerDuty {

  lazy val connector = new ThresholdConnectorImpl(mockHttp, mockPagerDuty)

  def mockGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, appConfig.desHeaders, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  "getThreshold" must {

    "return OK status with the threshold from ITMP" in {
      val result = UCThreshold(500.50)
      mockGet(connector.itmpThresholdURL)(Some(HttpResponse(200, Some(Json.toJson(result))))) // scalastyle:ignore magic.number
      Await.result(connector.getThreshold().value, 5.seconds) shouldBe Right(result.thresholdAmount)
    }

    "return an error and trigger a pagerDutyAlert when parsing invalid json" in {
      val result = 500.50
      inSequence {
        mockGet(connector.itmpThresholdURL)(Some(HttpResponse(200, Some(Json.toJson(result))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in threshold response")
        //mockPagerDuty.alert("Failed to make call to get threshold")
      }

      Await.result(connector.getThreshold().value, 5.seconds).isLeft shouldBe true
    }

    "return an error" when {
      "the call fails" in {
        inSequence {
          mockGet(connector.itmpThresholdURL)(None)
          mockPagerDutyAlert("Failed to make call to get threshold")
        }

        Await.result(connector.getThreshold().value, 5.seconds).isLeft shouldBe true
      }
    }
  }

}
