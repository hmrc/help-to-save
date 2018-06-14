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

import org.joda.time.LocalDate
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsNull, Json, Writes}
import uk.gov.hmrc.helptosave.models.UCThreshold
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext

import scala.concurrent.{ExecutionContext, Future}

class DESConnectorSpec extends TestSupport with GeneratorDrivenPropertyChecks with MockPagerDuty with TestData {
  MdcLoggingExecutionContext
  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  val nino = "NINO"

  lazy val connector = new DESConnectorImpl(mockHttp, mockMetrics, mockPagerDuty)

  def mockGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, appConfig.desHeaders, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  def mockPayeGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, appConfig.desHeaders + connector.originatorIdHeader, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  def mockPut[A](url: String, body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp.put(_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, appConfig.desHeaders, *, *, *)
      .returning(result.fold[Future[HttpResponse]](Future.failed(new Exception("")))(Future.successful))

  "the isEligible method" when {

      def url(nino: NINO): String = s"${connector.itmpEnrolmentURL}/help-to-save/eligibility-check/$nino"

    "return 200 status when call to DES successfully returns eligibility check response" in {
      mockGet(url(nino))(Some(HttpResponse(200, Some(Json.toJson(eligibilityCheckResultJson)))))
      val result = await(connector.isEligible(nino, None))

      result.status shouldBe 200
    }

    "return 500 status when call to DES fails" in {
      mockGet(url(nino))(Some(HttpResponse(500, Some(Json.toJson(eligibilityCheckResultJson)))))
      val result = await(connector.isEligible(nino, None))

      result.status shouldBe 500
    }
  }

  "the setFlag method" when {

      def url(nino: NINO): String = s"${connector.itmpEnrolmentURL}/help-to-save/accounts/$nino"

    "setting the ITMP flag" must {

      "return 200 status if the call to DES is successful" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(200)))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 200
      }

      "return 403 status if the call to DES comes back with a 403 (FORBIDDEN) status" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(403)))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 403
      }

      "return 500 status when call to DES fails" in {
        mockPut(url(nino), JsNull)(Some(HttpResponse(500)))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 500
      }

    }

  }

  "the getPersonalDetails method" must {

    val nino = randomNINO()
    val url = connector.payePersonalDetailsUrl(nino)

    "return pay personal details for a successful nino" in {
      mockPayeGet(url)(Some(HttpResponse(200, Some(Json.parse(payeDetails(nino)))))) // scalastyle:ignore magic.number
      val result = await(connector.getPersonalDetails(nino))

      result.status shouldBe 200
      Json.parse(result.body) shouldBe Json.parse(payeDetails(nino))
    }

    "return 500 status when call to DES fails" in {
      mockPayeGet(url)(Some(HttpResponse(500, Some(Json.parse(payeDetails(nino)))))) // scalastyle:ignore magic.number
      val result = await(connector.getPersonalDetails(nino))

      result.status shouldBe 500
    }

  }

  "the getThreshold method" must {

    val url = connector.itmpThresholdURL
    val result = UCThreshold(500.50)

    "return 200 status when call to get threshold from DES has been successful" in {
      mockGet(url)(Some(HttpResponse(200, Some(Json.toJson(result)))))

      val response = await(connector.getThreshold())
      response.status shouldBe 200
    }

    "return 500 status when call to DES fails" in {
      mockGet(url)(Some(HttpResponse(500, Some(Json.toJson(result)))))

      val response = await(connector.getThreshold())
      response.status shouldBe 500
    }
  }

}
