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
import play.api.libs.json.{JsDefined, JsSuccess, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.Helpers.contentAsJson
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestData
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec extends StrideAuthSupport with DefaultAwaitTimeout with TestData {

  class TestApparatus {
    val nino = "AE123456D"
    val txnId = UUID.randomUUID()

    val helpToSaveService = mock[HelpToSaveService]
    val payeDetailsConnector = mock[DESConnector]
    val enrolmentStore = mock[EnrolmentStore]

    def doEligibilityRequest(controller: StrideController): Future[PlayResult] =
      controller.eligibilityCheck(nino)(FakeRequest())

    def doPayeDetailsRequest(controller: StrideController): Future[PlayResult] =
      controller.getPayePersonalDetails(nino)(FakeRequest())

    def mockEligibilityService(nino: NINO)(result: Either[String, EligibilityCheckResult]): Unit =
      (helpToSaveService.getEligibility(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    def mockPayeDetailsConnector(nino: NINO)(result: Either[String, PayePersonalDetails]): Unit =
      (helpToSaveService.getPersonalDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    def mockEnrolmentStoreGet(nino: NINO)(result: Either[String, EnrolmentStore.Status]): Unit =
      (enrolmentStore.get(_: NINO)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(EitherT.fromEither[Future](result))

    val controller = new StrideController(helpToSaveService, payeDetailsConnector, mockAuthConnector, enrolmentStore)
  }

  "The StrideController" when {

    "handling requests to get enrolment status" must {

      "ask the enrolment store for the enrolment status and return the result" in new TestApparatus {
        List[EnrolmentStore.Status](
          EnrolmentStore.Enrolled(true),
          EnrolmentStore.Enrolled(false),
          EnrolmentStore.NotEnrolled
        ).foreach{ status ⇒
            inSequence {
              mockSuccessfulAuthorisation()
              mockEnrolmentStoreGet(nino)(Right(status))
            }

            val result = controller.getEnrolmentStatus(nino)(FakeRequest())
            contentAsJson(result).validate[EnrolmentStore.Status] shouldBe JsSuccess(status)
          }
      }

      "return an error if there is a problem getting the enrolment status" in new TestApparatus {
        inSequence {
          mockSuccessfulAuthorisation()
          mockEnrolmentStoreGet(nino)(Left(""))
        }

        val result = controller.getEnrolmentStatus(nino)(FakeRequest())
        status(result) shouldBe 500
      }

    }

    "handling requests to perform eligibility checks" must {

      "ask the EligibilityCheckerService if the user is eligible and return the result" in new TestApparatus {
        val eligibility = EligibilityCheckResult("x", 0, "y", 0)
        inSequence {
          mockSuccessfulAuthorisation()
          mockEligibilityService(nino)(Right(eligibility))
        }

        val result = doEligibilityRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.toJson(eligibility)
      }

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        inSequence {
          mockSuccessfulAuthorisation()
          mockEligibilityService(nino)(Left("The Eligibility Check service is unavailable"))
        }

        val result = doEligibilityRequest(controller)
        status(result) shouldBe 500
      }

    }

    "handling requests to Get paye-personal-details from DES" must {

      "ask the payeDetailsService for the personal details and return successful result for a valid nino" in new TestApparatus {
        inSequence {
          mockSuccessfulAuthorisation()
          mockPayeDetailsConnector(nino)(Right(ppDetails))
        }

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 200
        contentAsJson(result) shouldBe Json.toJson(ppDetails)
      }

      "return with a status 500 if the paye-personal-details call fails" in new TestApparatus {
        inSequence {
          mockSuccessfulAuthorisation()
          mockPayeDetailsConnector(nino)(Left(""))
        }

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 500
      }

      "return with a status 500 and empty json if the pay details is NOT_FOUND in DES" in new TestApparatus {
        inSequence {
          mockSuccessfulAuthorisation()
          mockPayeDetailsConnector(nino)(Left("Could not parse JSON response from paye-personal-details, received 200 (OK)"))
        }

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 500
      }
    }
  }

}
