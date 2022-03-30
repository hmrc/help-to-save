/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.AuthWithCL200
import uk.gov.hmrc.helptosave.models.UserCapResponse
import uk.gov.hmrc.helptosave.services.UserCapService
import uk.gov.hmrc.helptosave.util.toFuture

import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class UserCapControllerSpec extends AuthSupport {

  val userCapService = mock[UserCapService]

  val controller = new UserCapController(userCapService, mockAuthConnector, testCC)

  implicit val timeout: Timeout = Timeout(5, TimeUnit.SECONDS)

  "The UserCapController" when {

    "checking if account creation is allowed " should {
      "return successful result" in {
        (userCapService.isAccountCreateAllowed()(_: ExecutionContext))
          .expects(*)
          .returning(toFuture(UserCapResponse()))

        mockAuth(AuthWithCL200, Retrievals.nino)(Right(mockedNinoRetrieval))
        val result = controller.isAccountCreateAllowed()(FakeRequest())

        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.parse("""{"isDailyCapReached":false, "isTotalCapReached":false, "isDailyCapDisabled":false, "isTotalCapDisabled":false}""")
      }
    }
  }
}
