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
import play.api.libs.json.{Format, JsSuccess, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosave.models.{MissingUserInfo, UserInfo, randomUserInfo}
import uk.gov.hmrc.helptosave.services.UserInfoService
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.MissingUserInfos
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UserInformationControllerSpec extends TestSupport {

  class TestApparatus {

    val userInfoService = mock[UserInfoService]

    val controller = new UserInformationController(userInfoService)

    def mockUserInfoService(userDetailsURI: String, nino: String)(result: Either[UserInfoServiceError, UserInfo]): Unit =
      (userInfoService.getUserInfo(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userDetailsURI, nino, *, *)
        .returning(EitherT.fromEither[Future](result))

  }

  "The UserInformationController" must {

      def doRequest(nino:           String,
                    userDetailsURI: String,
                    controller:     UserInformationController): Future[Result] =
        controller.getUserInformation(nino, userDetailsURI)(FakeRequest())

    val nino = "nino"
    val userDetailsURI = "user-details"

    "ask the UserInfoAPIService for user info if the user is eligible" in new TestApparatus {
      inSequence {
        mockUserInfoService(userDetailsURI, nino)(Left(UserInfoServiceError.UserDetailsError("Oh no!")))
      }
      await(doRequest(nino, userDetailsURI, controller))

    }

    "return the user details once it retrieves them" in new TestApparatus {
      val userInfo = randomUserInfo()

      mockUserInfoService(userDetailsURI, nino)(Right(userInfo))

      val result = doRequest(nino, userDetailsURI, controller)
      status(result) shouldBe 200

      Json.fromJson[UserInfo](contentAsJson(result)) shouldBe JsSuccess(userInfo)
    }

    "report any missing user details back to the user" in new TestApparatus {
      List(MissingUserInfo.GivenName, MissingUserInfo.Surname, MissingUserInfo.DateOfBirth,
           MissingUserInfo.Email, MissingUserInfo.Contact).permutations.foreach { missingInfo ⇒
          inSequence {
            mockUserInfoService(userDetailsURI, nino)(Left(UserInfoServiceError.MissingUserInfos(missingInfo.toSet)))
          }

          val result = doRequest(nino, userDetailsURI, controller)
          status(result) shouldBe 200

          val jsValue = Json.fromJson[MissingUserInfos](contentAsJson(result))
          jsValue.isSuccess shouldBe true
          jsValue.get.missingInfo shouldBe missingInfo.toSet
        }
    }

    "return with a status 500 if the user info service fails" in new TestApparatus {
      List(
        UserInfoServiceError.UserDetailsError("Uh oh"),
        UserInfoServiceError.CitizenDetailsError("Oh no")
      ).foreach { e ⇒
          mockUserInfoService(userDetailsURI, nino)(Left(e))

          val result = doRequest(nino, userDetailsURI, controller)
          status(result) shouldBe 500
        }
    }
  }
}
