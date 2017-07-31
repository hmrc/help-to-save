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
import uk.gov.hmrc.helptosave.connectors.EligibilityCheckConnector
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.UserInfoService
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class EligibilityCheckerControllerSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val eligConnector = mock[EligibilityCheckConnector]
    val userInfoService = mock[UserInfoService]

    def doRequest(nino: String,
                  oauthAuthorisationCode: String,
                  controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck(nino, oauthAuthorisationCode)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Option[Boolean]): Unit =
      (eligConnector.isEligible(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromOption[Future](result, "mocking failed eligibility check"))


    def mockUserInfoService(userDetailsURI: String, nino: String)(result: Either[UserInfoServiceError,UserInfo]): Unit =
      (userInfoService.getUserInfo(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userDetailsURI, nino, *, *)
      .returning(EitherT.fromEither[Future](result))

    val controller = new EligibilityCheckController(eligConnector, userInfoService)
  }

  "The EligibilityCheckerController" when {

    "handling requests to perform eligibility checks" must {

      val userDetailsURI = "user-details-uri"
      val nino = randomNINO()

      def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "ask the EligibilityCheckerService if the user is eligible" in new TestApparatus {
        mockEligibilityCheckerService(nino)(None)
        await(doRequest(nino, userDetailsURI, controller))
      }

      "ask the UserInfoAPIService for user info if the user is eligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoService(userDetailsURI, nino)(Left(UserInfoServiceError.UserDetailsError("Oh no!")))
        }
        await(doRequest(nino, userDetailsURI, controller))

      }

      "return the user details once it retrieves them" in new TestApparatus {
        val userInfo = randomUserInfo()

          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoService(userDetailsURI, nino)(Right(userInfo))
          }

          val result = doRequest(nino, userDetailsURI, controller)
          status(result) shouldBe 200

          val jsValue = Json.fromJson[EligibilityCheckResult](contentAsJson(result))
          jsValue.isSuccess shouldBe true
          jsValue.get.result.isRight shouldBe true

          val checkResult = jsValue.get.result.right.get.get

        checkResult shouldBe userInfo
      }


      "report any missing user details back to the user" in new TestApparatus {

        List(MissingUserInfo.GivenName, MissingUserInfo.Surname, MissingUserInfo.DateOfBirth,
          MissingUserInfo.Email, MissingUserInfo.Contact).foreach{ missingInfo ⇒

          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoService(userDetailsURI, nino)(Left(UserInfoServiceError.MissingUserInfos(Set(missingInfo))))
          }

          val result = doRequest(nino, userDetailsURI, controller)
          status(result) shouldBe 200

          val jsValue = Json.fromJson[EligibilityCheckResult](contentAsJson(result))
          jsValue.isSuccess shouldBe true
          jsValue.get.result.isLeft shouldBe true

          val checkResult = jsValue.get.result.left.get
          checkResult.missingInfo shouldBe Set(missingInfo)
        }
      }

      "not ask the UserInfoService for user info if the user is ineligible" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(Some(false))
        }

        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 200
        Json.fromJson[EligibilityCheckResult](contentAsJson(result)).get shouldBe
          EligibilityCheckResult(Right(None))
      }

      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        inSequence {
          mockEligibilityCheckerService(nino)(None)
        }

        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 500
      }

      "return with a status 500 if the user info service fails" in new TestApparatus {
        List(
          UserInfoServiceError.UserDetailsError("Uh oh"),
          UserInfoServiceError.CitizenDetailsError("Oh no")
        ).foreach{ e ⇒
          inSequence {
            mockEligibilityCheckerService(nino)(Some(true))
            mockUserInfoService(userDetailsURI, nino)(Left(e))
          }

          val result = doRequest(nino, userDetailsURI, controller)
          status(result) shouldBe 500
        }


      }

    }
  }

}
