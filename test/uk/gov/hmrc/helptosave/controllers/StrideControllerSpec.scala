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

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsDefined, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.Helpers.contentAsJson
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, PayePersonalDetailsConnector}
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestData
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class StrideControllerSpec extends StrideAuthSupport with DefaultAwaitTimeout with TestData {

  class TestApparatus {
    val nino = "AE123456D"
    val ninoEncoded = "QUUxMjM0NTZE" //base64 Encoded

    val eligibilityConnector = mock[EligibilityCheckConnector]
    val payeDetailsConnector = mock[PayePersonalDetailsConnector]

    def doEligibilityRequest(controller: StrideController): Future[PlayResult] =
      controller.eligibilityCheck(ninoEncoded)(FakeRequest())

    def doPayeDetailsRequest(controller: StrideController): Future[PlayResult] =
      controller.getPayePersonalDetails(ninoEncoded)(FakeRequest())

    def mockEligibilityConnector(nino: NINO)(result: Either[String, Option[EligibilityCheckResult]]): Unit =
      (eligibilityConnector.isEligible(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    def mockPayeDetailsConnector(nino: NINO)(result: Either[String, Option[PayePersonalDetails]]): Unit =
      (payeDetailsConnector.getPersonalDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    val controller = new StrideController(eligibilityConnector, payeDetailsConnector, mockAuthConnector)
  }

  "The StrideController" when {

    "handling requests to perform eligibility checks" must {

        def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "ask the EligibilityCheckerService if the user is eligible and return the result" in new TestApparatus {
        val eligibility = EligibilityCheckResult("x", 0, "y", 0)
        mockSuccessfulAuthorisation()
        mockEligibilityConnector(nino)(Right(Some(eligibility)))

        val result = doEligibilityRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) \ "response" shouldBe JsDefined(Json.toJson(eligibility))
      }

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        mockSuccessfulAuthorisation()
        mockEligibilityConnector(nino)(Left("The Eligibility Check service is unavailable"))

        val result = doEligibilityRequest(controller)
        status(result) shouldBe 500
      }

      "return with a status 200 and empty json if the nino is NOT_FOUND as its not in receipt of Tax Credit" in new TestApparatus {
        mockSuccessfulAuthorisation()
        mockEligibilityConnector(nino)(Right(None))

        val result = doEligibilityRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.parse("{ }")
      }

    }

    "handling requests to Get paye-personal-details from DES" must {

      "ask the payeDetailsService for the personal details and return successful result for a valid nino" in new TestApparatus {
        mockSuccessfulAuthorisation()
        mockPayeDetailsConnector(nino)(Right(Some(ppDetails)))

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) \ "payeDetails" shouldBe JsDefined(Json.toJson(ppDetails))
      }

      "return with a status 500 if the paye-personal-details call fails" in new TestApparatus {
        mockSuccessfulAuthorisation()
        mockPayeDetailsConnector(nino)(Left("The paye-personal-details service is unavailable"))

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 500
      }

      "return with a status 200 and empty json if the nino is NOT_FOUND in DES" in new TestApparatus {
        mockSuccessfulAuthorisation()
        mockPayeDetailsConnector(nino)(Right(None))

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.parse("{ }")
      }
    }
  }

}
