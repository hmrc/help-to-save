/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.{Result ⇒ PlayResult}
import uk.gov.hmrc.helptosave.controllers.EligibilityCheckerControllerSpec.TestUserInfo
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.{EligibilityCheckService, UserInfoAPIService}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class EligibilityCheckerControllerSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val eligibilityCheckService = mock[EligibilityCheckService]
    val userInfoAPIService = mock[UserInfoAPIService]

    def doRequest(nino: String,
                  oauthAuthorisationCode: String,
                  controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck(nino, oauthAuthorisationCode)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Option[Boolean]): Unit =
      (eligibilityCheckService.isEligible(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromOption[Future](result, "mocking failed eligibility check"))


    def mockUserInfoAPIService(authorisationCode: String, nino: String)(result: Option[OpenIDConnectUserInfo]): Unit =
      (userInfoAPIService.getUserInfo(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authorisationCode, nino, *, *)
      .returning(EitherT.fromOption[Future](result, "Oh no!"))

    val controller = new EligibilityCheckController(eligibilityCheckService, userInfoAPIService)
  }

  "The EligibilityCheckerController" when {

    "handling requests to perform eligibility checks" must {

      val oauthAuthorisationCode = "authorisation-code"
      val nino = randomNINO()

      def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "ask the EligibilityCheckerService if the user is eligible" in new TestApparatus {
        mockEligibilityCheckerService(nino)(None)
        await(doRequest(nino, oauthAuthorisationCode, controller))
      }

      "ask the UserInfoAPIService for user info if the user is eligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(None)
        }
        await(doRequest(nino, oauthAuthorisationCode, controller))

      }

      "return the user details once it retrieves them if all fields are present" in new TestApparatus {
          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoAPIService(oauthAuthorisationCode, nino)(Some(TestUserInfo.apiUserInfo))
          }

          val result = doRequest(nino, oauthAuthorisationCode, controller)
          status(result) shouldBe 200

          val jsValue = Json.fromJson[EligibilityCheckResult](contentAsJson(result))
          jsValue.isSuccess shouldBe true
          jsValue.get.result.isDefined shouldBe true

          val checkResult = jsValue.get.result.get

        checkResult shouldBe TestUserInfo.userInfo(nino)
      }


      "return a 500 if user details are missing" in new TestApparatus {

        def test(userInfo: OpenIDConnectUserInfo): Unit = {
          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoAPIService(oauthAuthorisationCode, nino)(Some(userInfo))
          }

          val result = doRequest(nino, oauthAuthorisationCode, controller)
          status(result) shouldBe 500
        }

        test(TestUserInfo.apiUserInfo.copy(given_name = None))
        test(TestUserInfo.apiUserInfo.copy(family_name = None))
        test(TestUserInfo.apiUserInfo.copy(birthdate = None))
        test(TestUserInfo.apiUserInfo.copy(email = None))
        test(TestUserInfo.apiUserInfo.copy(address = None))
      }



      "not ask the UserInfoService for user info if the user is ineligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(false))
        }

        val result = doRequest(nino, oauthAuthorisationCode, controller)
        status(result) shouldBe 200
        Json.fromJson[EligibilityCheckResult](contentAsJson(result)).get shouldBe
          EligibilityCheckResult(None)
      }

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(None)
        }

        val result = doRequest(nino, oauthAuthorisationCode, controller)
        status(result) shouldBe 500
      }

      "return with a status 500 if the user info API service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(None)
        }

        val result = doRequest(nino, oauthAuthorisationCode, controller)
        status(result) shouldBe 500
      }

    }
  }

}


object EligibilityCheckerControllerSpec {

  object TestUserInfo{
    val forename = "Forename"
    val surname = "Surname"
    val addressLines = List("line1", "line2")
    val postcode = "postcode"
    val countryCode = "countrycode"
    val email = "email"
    val dateOfBirth = LocalDate.now()

    val apiUserInfo = OpenIDConnectUserInfo(
      Some(forename), Some(surname), None,
      Some(OpenIDConnectUserInfo.Address(addressLines.mkString("\n"), Some(postcode), None, Some(countryCode))),
      Some(dateOfBirth), None, None, Some(email)
    )

    def userInfo(nino: NINO) = UserInfo(
      forename, surname, nino, dateOfBirth, email, Address(addressLines, Some(postcode), Some(countryCode.take(2))))
  }

}