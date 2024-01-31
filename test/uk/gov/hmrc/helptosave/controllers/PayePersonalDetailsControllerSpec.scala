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

package uk.gov.hmrc.helptosave.controllers

import java.util.UUID

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.{Result => PlayResult}
import play.api.test.Helpers.contentAsJson
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestData
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class PayePersonalDetailsControllerSpec extends StrideAuthSupport with DefaultAwaitTimeout with TestData {

  class TestApparatus {
    val nino = "AE123456D"
    val txnId = UUID.randomUUID()

    val helpToSaveService = mock[HelpToSaveService]
    val payeDetailsConnector = mock[DESConnector]

    def doPayeDetailsRequest(controller: PayePersonalDetailsController): Future[PlayResult] =
      controller.getPayePersonalDetails(nino)(FakeRequest())

    def mockPayeDetailsConnector(nino: NINO)(result: Either[String, PayePersonalDetails]): Unit =
      (helpToSaveService
        .getPersonalDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromEither[Future](result))

    val controller = new PayePersonalDetailsController(helpToSaveService, mockAuthConnector, testCC)
  }

  "The PayePersonalDetailsController" when {

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
          mockPayeDetailsConnector(nino)(
            Left("Could not parse JSON response from paye-personal-details, received 200 (OK)"))
        }

        val result = doPayeDetailsRequest(controller)
        status(result) shouldBe 500
      }
    }
  }

}
