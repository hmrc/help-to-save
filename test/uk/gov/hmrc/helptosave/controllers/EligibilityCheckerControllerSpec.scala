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
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.{Result ⇒ PlayResult}
import uk.gov.hmrc.helptosave.models.{EligibilityCheckResult, UserInfo, randomUserDetails}
import uk.gov.hmrc.helptosave.services.{EligibilityCheckerService, UserInfoService}
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class EligibilityCheckerControllerSpec extends WordSpec with Matchers with MockFactory {


  class TestApparatus {
    val eligibilityCheckService = mock[EligibilityCheckerService]
    val userInfoService = mock[UserInfoService]

    def doRequest(nino: String,
                  userDetailsURI: String,
                  controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck(nino, userDetailsURI)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Option[Boolean]): Unit =
      (eligibilityCheckService.getEligibility(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning{
          result.fold(
            EitherT.left[Future,String,Boolean](Future.successful("mocking failed eligibility check"))
          )(eligible ⇒
            Result(Future.successful(eligible))
          )
        }

    def mockUserInfoService(nino: NINO, userDetailsURI: String)(userInfo: Option[UserInfo]): Unit =
      (userInfoService.getUserInfo(_: String, _: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(userDetailsURI, nino, *, *)
        .returning{
          userInfo.fold(
            EitherT.left[Future,String,UserInfo](Future.successful("mocking failed user info"))
          )(info ⇒
            Result(Future.successful(info))
          )
        }

    val controller = new EligibilityCheckController(eligibilityCheckService, userInfoService)
  }

  "The EligibilityCheckerController" when {

    "handling requests to perform eligibility checks" must {

      val nino = "nino"
      val userDetailsURI = "uri"
      val userDetails = randomUserDetails()

      "ask the EligibilityCheckerService if the user is eligible" in  new TestApparatus{
        mockEligibilityCheckerService(nino)(Some(true))
        doRequest(nino, userDetailsURI, controller)
      }

      "ask the UserInfoService for user info if the user is eligible" in  new TestApparatus{
        inSequence{
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoService(nino, userDetailsURI)(Some(userDetails))
        }
        Await.result(doRequest(nino, userDetailsURI, controller), 3.seconds)
      }

      "return the user details once it retrieves them" in new TestApparatus{
        inSequence{
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoService(nino, userDetailsURI)(Some(userDetails))
        }
        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 200
        Json.fromJson[EligibilityCheckResult](contentAsJson(result)).get shouldBe
          EligibilityCheckResult(Some(userDetails))
      }

      "not ask the UserInfoService for user info if the user is ineligible" in new TestApparatus {
        inSequence{
          mockEligibilityCheckerService(nino)(Some(false))
        }

        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 200
        Json.fromJson[EligibilityCheckResult](contentAsJson(result)).get shouldBe
          EligibilityCheckResult(None)
      }

      "return with a status 500 if the eligibililty check service fails" in new TestApparatus{
        inSequence{
          mockEligibilityCheckerService(nino)(None)
        }

        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 500
      }

      "return with a status 500 if the user info service fails" in new TestApparatus{
        inSequence{
          mockEligibilityCheckerService(nino)(Some(true))
          mockUserInfoService(nino, userDetailsURI)(None)
        }

        val result = doRequest(nino, userDetailsURI, controller)
        status(result) shouldBe 500
      }

    }
  }

}
