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

package uk.gov.hmrc.helptosave.controllers

import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsDefined, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth._
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.EligibilityCheckService
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityCheckerControllerSpec extends AuthSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val eligibilityService = mock[EligibilityCheckService]

    def doRequest(controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck()(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Either[String, EligibilityCheckResult]): Unit =
      (eligibilityService.getEligibility(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    val controller = new EligibilityCheckController(eligibilityService, mockAuthConnector)
  }

  "The EligibilityCheckerController" when {

    val txnId = UUID.randomUUID()

    "handling requests to perform eligibility checks" must {

      val userDetailsURI = "user-details-uri"

        def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        mockAuthResultWithSuccess(AuthWithCL200)(mockedNinoRetrieval)
        mockEligibilityCheckerService(nino)(Left("The Eligibility Check service is unavailable"))

        val result = doRequest(controller)
        status(result) shouldBe 500
      }

      "return the eligibility status returned from the eligibility check service if " +
        "successful" in new TestApparatus {
          val eligibility = EligibilityCheckResult("x", 0, "y", 0)
          mockAuthResultWithSuccess(AuthWithCL200)(mockedNinoRetrieval)
          mockEligibilityCheckerService(nino)(Right(eligibility))

          val result = doRequest(controller)
          status(result) shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(eligibility)
        }

    }
  }

}
