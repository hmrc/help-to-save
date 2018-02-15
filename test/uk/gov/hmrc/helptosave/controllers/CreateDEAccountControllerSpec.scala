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

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.Status.{BAD_REQUEST, CREATED}
import uk.gov.hmrc.helptosave.connectors.HelpToSaveProxyConnector
import uk.gov.hmrc.helptosave.models.NSIUserInfo
import uk.gov.hmrc.helptosave.util.toFuture
import uk.gov.hmrc.helptosave.utils.{TestEnrolmentBehaviour, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class CreateDEAccountControllerSpec extends TestSupport with TestEnrolmentBehaviour {

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  class TestApparatus {
    val proxyConnector = mock[HelpToSaveProxyConnector]

    val controller = new CreateDEAccountController(enrolmentStore, itmpConnector, proxyConnector)
  }

  "The CreateAccountController" when {

    "creating an account for a DE user" must {

      "create account if the request is valid NSIUserInfo json" in new TestApparatus {
        inSequence {

          (proxyConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
            .expects(*, *, *)
            .returning(toFuture(HttpResponse(CREATED)))

          mockEnrolmentStoreUpdate("nino", false)(Right(()))
          mockITMPConnector("nino")(Right(()))
          mockEnrolmentStoreUpdate("nino", true)(Right(()))
        }

        val requestBody = Json.parse(jsonString("20200101"))
        val result = controller.createDEAccount()(FakeRequest().withJsonBody(requestBody))

        status(result) shouldBe CREATED
      }

      "create account if the request is valid NSIUserInfo json even if write to mongo fails" in new TestApparatus {
        inSequence {

          (proxyConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
            .expects(*, *, *)
            .returning(toFuture(HttpResponse(CREATED)))

          mockEnrolmentStoreUpdate("nino", false)(Left(""))
        }

        val requestBody = Json.parse(jsonString("20200101"))
        val result = controller.createDEAccount()(FakeRequest().withJsonBody(requestBody))

        status(result) shouldBe CREATED
      }

      "return bad request response if the request body is not a valid NSIUserInfo json" in new TestApparatus {

        val requestBody = Json.parse(jsonString("\"123456\""))
        val result = controller.createDEAccount()(FakeRequest().withJsonBody(requestBody))
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() should include("error.expected.date.isoformat")
      }

      "return bad request response if there is no json the in the request body" in new TestApparatus {
        val result = controller.createDEAccount()(FakeRequest())
        status(result) shouldBe BAD_REQUEST
        contentAsJson(result).toString() shouldBe """{"errorMessageId":"","errorMessage":"No JSON found in request body","errorDetails":""}"""
      }

        def jsonString(dobValue: String): String =
          s"""{
           | "nino" : "nino",
           | "forename" : "name",
           | "surname" : "surname",
           | "dateOfBirth" : $dobValue,
           | "contactDetails" : {
           |     "address1" : "1",
           |     "address2" : "2",
           |     "postcode": "postcode",
           |     "countryCode" : "country",
           |     "communicationPreference" : "preference"
           | },
           | "registrationChannel" : "channel"
           |}
           """.stripMargin

    }
  }

}
