/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.helptosave.models.{UCResponse, UCThreshold}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.HttpResponse

class DESConnectorSpec extends TestSupport with MockPagerDuty with TestData with HttpSupport with ScalaCheckDrivenPropertyChecks {
  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  val nino = "NINO"

  lazy val connector = new DESConnectorImpl(mockHttp, servicesConfig)
  val returnHeaders = Map[String, Seq[String]]()

  "the isEligible method" when {

      def url(nino: NINO): String = s"${connector.itmpECBaseURL}/help-to-save/eligibility-check/$nino"

    "return 200 status when call to DES successfully returns eligibility check response" in {
      List[(Option[UCResponse], Map[String, String])](
        Some(UCResponse(ucClaimant      = true, withinThreshold = Some(true))) →
          Map("universalCreditClaimant" → "Y", "withinThreshold" -> "Y"),
        Some(UCResponse(ucClaimant      = true, withinThreshold = Some(false))) →
          Map("universalCreditClaimant" → "Y", "withinThreshold" -> "N"),
        Some(UCResponse(ucClaimant      = false, withinThreshold = None)) →
          Map("universalCreditClaimant" → "N"),
        None →
          Map()
      ).foreach{
          case (ucResponse, expectedQueryParameters) ⇒
            withClue(s"For ucResponse: $ucResponse:"){
              mockGet(url(nino), expectedQueryParameters, appConfig.desHeaders)(Some(HttpResponse(200, Json.toJson(eligibilityCheckResultJson), returnHeaders)))
              val result = await(connector.isEligible(nino, ucResponse))

              result.status shouldBe 200
            }
        }

    }

    "return 500 status when call to DES fails" in {
      mockGet(url(nino), headers = appConfig.desHeaders)(Some(HttpResponse(500, Json.toJson(eligibilityCheckResultJson), returnHeaders)))
      val result = await(connector.isEligible(nino, None))

      result.status shouldBe 500
    }
  }

  "the setFlag method" when {

      def url(nino: NINO): String = s"${connector.itmpECBaseURL}/help-to-save/accounts/$nino"

    "setting the ITMP flag" must {

      "return 200 status if the call to DES is successful" in {
        mockPut(url(nino), JsNull, appConfig.desHeaders)(Some(HttpResponse(200, "200")))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 200
      }

      "return 403 status if the call to DES comes back with a 403 (FORBIDDEN) status" in {
        mockPut(url(nino), JsNull, appConfig.desHeaders)(Some(HttpResponse(403, "403")))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 403
      }

      "return 500 status when call to DES fails" in {
        mockPut(url(nino), JsNull, appConfig.desHeaders)(Some(HttpResponse(500, "")))
        val result = await(connector.setFlag(nino))

        result.status shouldBe 500
      }

    }

  }

  "the getPersonalDetails method" must {

    val nino = randomNINO()
    val url = connector.payePersonalDetailsUrl(nino)

    "return pay personal details for a successful nino" in {
      mockGet(url, headers = appConfig.desHeaders + connector.originatorIdHeader)(Some(HttpResponse(200, Json.parse(payeDetails(nino)), returnHeaders))) // scalastyle:ignore magic.number
      val result = await(connector.getPersonalDetails(nino))

      result.status shouldBe 200
      Json.parse(result.body) shouldBe Json.parse(payeDetails(nino))
    }

    "return 500 status when call to DES fails" in {
      mockGet(url, headers = appConfig.desHeaders + connector.originatorIdHeader)(Some(HttpResponse(500, Json.parse(payeDetails(nino)), returnHeaders))) // scalastyle:ignore magic.number
      val result = await(connector.getPersonalDetails(nino))

      result.status shouldBe 500
    }

  }

  "the getThreshold method" must {

    val url = s"${connector.itmpECBaseURL}/universal-credits/threshold-amount"
    val result = UCThreshold(500.50)

    "return 200 status when call to get threshold from DES has been successful" in {
      mockGet(url, headers = appConfig.desHeaders)(Some(HttpResponse(200, Json.toJson(result), returnHeaders)))

      val response = await(connector.getThreshold())
      response.status shouldBe 200
    }

    "return 500 status when call to DES fails" in {
      mockGet(url, headers = appConfig.desHeaders)(Some(HttpResponse(500, Json.toJson(result), returnHeaders)))

      val response = await(connector.getThreshold())
      response.status shouldBe 500
    }
  }

}
