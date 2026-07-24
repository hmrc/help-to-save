/*
 * Copyright 2026 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.{HIPEligibilityCheckRequest, UCResponse}
import uk.gov.hmrc.helptosave.util.WireMockMethods
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestData, TestSupport}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.http.test.ResponseMatchers

class HIPEligibilityConnectorSpec
    extends TestSupport
    with MockPagerDuty
    with TestData
    with WireMockSupport
    with WireMockMethods
    with ResponseMatchers
    with EitherValues {

  override lazy val additionalConfig: Configuration =
    Configuration(
      "microservice.services.hip.host"                   -> wireMockHost,
      "microservice.services.hip.port"                   -> wireMockPort,
      "microservice.services.hip.root"                   -> "",
      "microservice.services.hip.environment"            -> "local",
      "microservice.services.hip.clientId"               -> "hip-client-id",
      "microservice.services.hip.clientSecret"           -> "hip-secret",
      "microservice.services.hip.originatorId"           -> "TEST-GOV-UK-ORIGINATOR-ID"
    )

  lazy val connector: HIPEligibilityConnector = injector.instanceOf[HIPEligibilityConnector]

  "checkEligibility" must {
    val nino = randomNINO()
    val url  = s"/help-to-save/$nino/account"

    "post fixed Tax Credit values and UC fields to HIP" in {
      stubFor(
        post(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                Json
                  .obj(
                    "result" -> "CUSTOMER ELIGIBLE FOR HTS ACCOUNT",
                    "reason" -> "IN RECEIPT OF DWP UC AND INCOME SUFFICIENT"
                  )
                  .toString
              )
          )
      )

      val ucResponse = Some(UCResponse(ucClaimant = true, withinThreshold = Some(false)))
      val result     = await(connector.checkEligibility(nino, ucResponse))

      result.value.status shouldBe 200
      verify(
        postRequestedFor(urlEqualTo(url))
          .withHeader("Authorization", equalTo("Basic aGlwLWNsaWVudC1pZDpoaXAtc2VjcmV0"))
          .withHeader("Environment", equalTo("local"))
          .withHeader("gov-uk-originator-id", equalTo("TEST-GOV-UK-ORIGINATOR-ID"))
          .withHeader("correlationId", matching("[0-9a-fA-F\\-]{36}"))
          .withRequestBody(equalToJson(Json.toJson(HIPEligibilityCheckRequest(ucResponse)).toString))
      )
    }

    "omit UC fields when UC details are unavailable" in {
      stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(200)))

      val result = await(connector.checkEligibility(nino, None))

      result.value.status shouldBe 200
      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(
            equalToJson(
              Json
                .obj(
                  "newTaxCreditStatus" -> "NINO not found",
                  "workingTaxCreditEntitlement" -> "Current Award does not include a WTC entitlement",
                  "workingTaxCreditTaperedHouseholdAward" -> 0,
                  "childTaxCreditTaperedHouseholdAward" -> 0
                )
                .toString
            )
          )
      )
    }
  }
}
