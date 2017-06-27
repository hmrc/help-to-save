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

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.{Result ⇒ PlayResult}
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.{EligibilityCheckerService, UserInfoAPIService, UserInfoService}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class EligibilityCheckerControllerSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val eligibilityCheckService = mock[EligibilityCheckerService]
    val userInfoAPIService = mock[UserInfoAPIService]
    val userInfoService = mock[UserInfoService]

    def doRequest(nino: String,
                  userDetailsURI: String,
                  oauthAuthorisationCode: String,
                  controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck(nino, userDetailsURI, oauthAuthorisationCode)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Option[Boolean]): Unit =
      (eligibilityCheckService.getEligibility(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromOption[Future](result, "mocking failed eligibility check"))

    def mockUserInfoService(nino: NINO, userDetailsURI: String)(userInfo: Option[UserInfo]): Unit =
      (userInfoService.getUserInfo(_: String, _: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userDetailsURI, nino, *, *)
        .returning {
          EitherT.fromOption[Future](userInfo, "mocking failed user info")
        }

    def mockUserInfoAPIService(authorisationCode: String, nino: String)(result: Option[OpenIDConnectUserInfo]): Unit =
      (userInfoAPIService.getUserInfo(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(authorisationCode, nino, *, *)
      .returning(EitherT.fromOption[Future](result, "Oh no!"))

    val controller = new EligibilityCheckController(eligibilityCheckService, userInfoAPIService, userInfoService)
  }

  "The EligibilityCheckerController" when {

    "handling requests to perform eligibility checks" must {

      val userDetailsURI = "uri"
      val oauthAuthorisationCode = "authorisation-code"
      val userDetails = randomUserInfo()
      val nino = randomNINO()

      def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "ask the EligibilityCheckerService if the user is eligible" in new TestApparatus {
        mockEligibilityCheckerService(nino)(None)
        await(doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller))
      }

      "ask the UserInfoAPIService for user info if the user is eligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(None)
        }
        await(doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller))

      }

      "ask the UserInfoService for user info if the user is eligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(Some(randomAPIUserInfo()))
          mockUserInfoService(nino, userDetailsURI)(None)
        }
        await(doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller))
      }

      "return the user details once it retrieves them" in new TestApparatus {
        forAll{ apiUserInfo: OpenIDConnectUserInfo ⇒
          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoAPIService(oauthAuthorisationCode, nino)(Some(apiUserInfo))
            mockUserInfoService(nino, userDetailsURI)(Some(userDetails))
          }

          val result = doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller)
          status(result) shouldBe 200

          val jsValue = Json.fromJson[EligibilityCheckResult](contentAsJson(result))
          jsValue.isSuccess shouldBe true
          jsValue.get.result.isDefined shouldBe true

          val checkResult = jsValue.get.result.get

          // test that the APIUserInfo field is favored over the corresponding UserInfo field
          def test[T](field: T)(extractor: OpenIDConnectUserInfo ⇒ Option[T], backup: UserInfo ⇒ T): Unit =
            field shouldBe extractor(apiUserInfo).getOrElse(backup(userDetails))

          test(checkResult.forename)(_.given_name, _.forename)
          test(checkResult.surname)(_.family_name, _.surname)
          test(checkResult.dateOfBirth)(_.birthdate, _.dateOfBirth)
          test(checkResult.address.lines)(_.address.map(_.formatted.split("\n").toList), _.address.lines)
          test(checkResult.address.postcode)(_.address.map(_.postal_code), _.address.postcode)
          test(checkResult.email)(_.email, _.email)

          // these fields don't come from APIUserInfo
          checkResult.nino shouldBe nino

          // there are no ISO country codes yet
          checkResult.address.country shouldBe None
        }
      }

      "not ask the UserInfoService for user info if the user is ineligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(false))
        }

        val result = doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller)
        status(result) shouldBe 200
        Json.fromJson[EligibilityCheckResult](contentAsJson(result)).get shouldBe
          EligibilityCheckResult(None)
      }

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(None)
        }

        val result = doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller)
        status(result) shouldBe 500
      }

      "return with a status 500 if the user info API service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(None)
        }

        val result = doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller)
        status(result) shouldBe 500
      }

      "return with a status 500 if the user info service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoAPIService(oauthAuthorisationCode, nino)(Some(randomAPIUserInfo()))
          mockUserInfoService(nino, userDetailsURI)(None)
        }

        val result = doRequest(nino, userDetailsURI, oauthAuthorisationCode, controller)
        status(result) shouldBe 500
      }

    }
  }

}
