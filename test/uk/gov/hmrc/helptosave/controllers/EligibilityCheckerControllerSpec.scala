/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.controllers

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, PAClientId}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{nino ⇒ v2Nino}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.authProviderId
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth._
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EligibilityCheckerControllerSpec extends StrideAuthSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val eligibilityService = mock[HelpToSaveService]

    def doRequest(controller: EligibilityCheckController, nino: Option[String]): Future[PlayResult] =
      controller.eligibilityCheck(nino)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Either[String, EligibilityCheckResponse]): Unit =
      (eligibilityService.getEligibility(_: NINO, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, routes.EligibilityCheckController.eligibilityCheck(Some(nino)).url, *, *)
        .returning(EitherT.fromEither[Future](result))

    val controller = new EligibilityCheckController(eligibilityService, mockAuthConnector)

    val privilegedCredentials = PAClientId("id")
  }

  "The EligibilityCheckerController" when {

    val ggCredentials = GGCredId("123-gg")
    val eligibility = EligibilityCheckResponse(EligibilityCheckResult("x", 0, "y", 0), Some(123.45))

    "handling requests to perform eligibility checks" must {

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEligibilityCheckerService(nino)(Left("The Eligibility Check service is unavailable"))

        val result = doRequest(controller, Some(nino))
        status(result) shouldBe 500
      }

      "return the eligibility status returned from the eligibility check service if " +
        "successful" in new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
          mockAuth(v2Nino)(Right(mockedNinoRetrieval))
          mockEligibilityCheckerService(nino)(Right(eligibility))

          val result = doRequest(controller, Some(nino))
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(eligibility)
        }

      "return Forbidden if the ggNino does not match the given nino" in new TestApparatus {
        mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))

        val result = doRequest(controller, Some("AE121212A"))
        status(result) shouldBe 403
      }

      "return the eligibility status returned from the eligibility check service successfully when no nino is given" in
        new TestApparatus {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
          mockAuth(v2Nino)(Right(mockedNinoRetrieval))
          mockEligibilityCheckerService(nino)(Right(eligibility))

          val result = doRequest(controller, None)
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(eligibility)
        }

    }

    "handling requests to perform stride or API eligibility checks" must {

      "ask the EligibilityCheckerService if the user is eligible and return the result" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
          mockEligibilityCheckerService(nino)(Right(eligibility))
        }

        val result = doRequest(controller, Some(nino))
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.toJson(eligibility)
      }

      "return with a 500 status if the eligibility check service fails" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
          mockEligibilityCheckerService(nino)(Left("The Eligibility Check service is unavailable"))
        }

        val result = doRequest(controller, Some(nino))
        status(result) shouldBe 500
      }

      "return a Bad Request(400) status if there was no nino given" in new TestApparatus {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
        }

        val result = doRequest(controller, None)
        status(result) shouldBe 400
      }

    }
  }

}
