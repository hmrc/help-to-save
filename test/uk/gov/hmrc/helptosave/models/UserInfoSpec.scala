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

package uk.gov.hmrc.helptosave.models

import java.time.LocalDate

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.helptosave.utils.TestSupport

class UserInfoSpec extends TestSupport {

  "UserInfo" must {

    "have a valid JSON format instance" in {
      val userInfo = UserInfo("nane", "surname", "nino", randomDate(), "email",
                                                         Address(List("address"), Some("postcode"), Some("Country")))

      val jsValue = Json.toJson(userInfo)
      Json.fromJson[UserInfo](jsValue) shouldBe JsSuccess(userInfo)
    }
  }
}
