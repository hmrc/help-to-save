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

package uk.gov.hmrc.helptosave.connectors

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.{UCResponse, UCThreshold}
import uk.gov.hmrc.helptosave.util.{NINO, WireMockMethods}
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.test.WireMockSupport

import java.net.URL
import java.time.LocalDate

class DESConnectorSpec
    extends TestSupport with MockPagerDuty with TestData with WireMockSupport with WireMockMethods with ScalaCheckDrivenPropertyChecks {

  val nino = "NINO"
  val date: LocalDate = LocalDate.of(2017, 6, 12) // scalastyle:ignore magic.number

  override lazy val additionalConfig: Configuration = {
    Configuration(
      "microservice.services.itmp-eligibility-check.host" -> wireMockHost,
      "microservice.services.itmp-eligibility-check.port" -> wireMockPort,
      "microservice.services.itmp-threshold.host" -> wireMockHost,
      "microservice.services.itmp-threshold.port" -> wireMockPort,
      "microservice.services.itmp-enrolment.host" -> wireMockHost,
      "microservice.services.itmp-enrolment.port" -> wireMockPort,
      "microservice.services.paye-personal-details.host" -> wireMockHost,
      "microservice.services.paye-personal-details.port" -> wireMockPort
    )
  }

  lazy val connector: DESConnector = fakeApplication.injector.instanceOf[DESConnector]
  val originatorIdHeader: Seq[(String, String)] = Seq("Originator-Id" -> originatorIdHeaderValue)

  "the isEligible method" when {

    def url(nino: NINO) = url"/help-to-save/eligibility-check/$nino"

    "return 200 status when call to DES successfully returns eligibility check response" in {
      List[(Option[UCResponse], Seq[(String, String)])](
        Some(UCResponse(ucClaimant = true, withinThreshold = Some(true))) ->
          Seq("universalCreditClaimant" -> "Y", "withinThreshold" -> "Y"),
        Some(UCResponse(ucClaimant = true, withinThreshold = Some(false))) ->
          Seq("universalCreditClaimant" -> "Y", "withinThreshold" -> "N"),
        Some(UCResponse(ucClaimant = false, withinThreshold = None)) ->
          Seq("universalCreditClaimant" -> "N"),
        None ->
          Seq()
      ).foreach {
        case (ucResponse, expectedQueryParameters) =>
          withClue(s"For ucResponse: $ucResponse:") {
            when(GET, url(nino).toString, expectedQueryParameters.toMap, appConfig.desHeaders.toMap)
              .thenReturn(200, Json.toJson(eligibilityCheckResultJson))

            val result = await(connector.isEligible(nino, ucResponse))
            result.status shouldBe 200
          }
      }

    }

    "return 500 status when call to DES fails" in {
      when(GET, url(nino).toString, headers = appConfig.desHeaders.toMap)
        .thenReturn(500, Json.toJson(eligibilityCheckResultJson))

      val result = await(connector.isEligible(nino, None))
      result.status shouldBe 500
    }
  }

  "the setFlag method" when {

    def url(nino: NINO) = url"/help-to-save/accounts/$nino"

    "setting the ITMP flag" must {

      "return 200 status if the call to DES is successful" in {
        when(PUT, url(nino).toString, headers = appConfig.desHeaders.toMap).thenReturn(200)

        val result = await(connector.setFlag(nino))
        result.status shouldBe 200
      }

      "return 403 status if the call to DES comes back with a 403 (FORBIDDEN) status" in {
        when(PUT, url(nino).toString, headers = appConfig.desHeaders.toMap).thenReturn(403)

        val result = await(connector.setFlag(nino))
        result.status shouldBe 403
      }

      "return 500 status when call to DES fails" in {
        when(PUT, url(nino).toString, headers = appConfig.desHeaders.toMap).thenReturn(500)

        val result = await(connector.setFlag(nino))
        result.status shouldBe 500
      }

    }

  }

  "the getPersonalDetails method" must {

    val nino = randomNINO()
    val url = url"/pay-as-you-earn/02.00.00/individuals/$nino"

    "return pay personal details for a successful nino" in {
      when(GET, url.toString, headers = appConfig.desHeaders.toMap ++ originatorIdHeader)
        .thenReturn(200, payeDetails(nino))

      val result = await(connector.getPersonalDetails(nino))

      result.status shouldBe 200
      result.body shouldBe payeDetails(nino)
    }

    "return 500 status when call to DES fails" in {
      when(GET, url.toString, headers = appConfig.desHeaders.toMap++ originatorIdHeader)
        .thenReturn(500, Json.toJson(Json.toJson(payeDetails(nino))))

      val result = await(connector.getPersonalDetails(nino))
      result.status shouldBe 500
    }

  }

  "the getThreshold method" must {

    val url = url"/universal-credits/threshold-amount"
    val result = UCThreshold(500.50)

    "return 200 status when call to get threshold from DES has been successful" in {
      when(GET, url.toString, headers = appConfig.desHeaders.toMap).thenReturn(200, Json.toJson(Json.toJson(result)))

      val response = await(connector.getThreshold())
      response.status shouldBe 200
    }

    "return 500 status when call to DES fails" in {
      when(GET, url.toString, headers = appConfig.desHeaders.toMap).thenReturn(500)

      val response = await(connector.getThreshold())
      response.status shouldBe 500
    }
  }

}
