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
package uk.gov.hmrc.helptosave

import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.helptosave.models.MissingUserInfo.{Email, Surname}
import uk.gov.hmrc.helptosave.models.{Address, UserInfo}
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.MissingUserInfos
import uk.gov.hmrc.helptosave.util.NINO

class MissingMandatoryData extends WordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with GuiceOneServerPerSuite {

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override lazy val port: Int = 7001

  val wsClient = app.injector.instanceOf[WSClient]

  val eligibilityCheckURL = "http://localhost:7001/help-to-save/eligibility-check"
  def userDetailsURL(encodedNINO: NINO) = s"http://localhost:7002/help-to-save-stub/user-details/${decode(encodedNINO)}"

  def decode(encodedNINO: String): NINO =
    new String(Base64.getDecoder.decode(encodedNINO))

  def checkEligibility(encodedNino: String, userDetailsURI: String): WSResponse =
    wsClient
      .url(s"$eligibilityCheckURL?nino=${decode(encodedNino)}&userDetailsURI=${URLEncoder.encode(userDetailsURI)}")
      .get()
      .futureValue


  // To do: convert to Gherkin/Cucumber
  "Checking if a user can create an account" when {

    "an applicant is eligible" when {

      "user details and citizen details return all the required fields" must {

        "return with a 200" in {
          val encodedNino = "QUcwMTAxMjND"
          val expected: UserInfo = UserInfo("Sarah", "Smith", decode(encodedNino), LocalDate.of(1999, 12, 12), "sarah@smith.com",
            Address(List("line 1", "line 2", "line 3", "line 4", "line 5"), Some("BN43 XXX"), Some("GB")))

          val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, userDetailsURL(encodedNino))

          checkEligibilityResponse.status shouldBe OK
          val json = Json.toJson(expected)
          (checkEligibilityResponse.json \ "result").get shouldBe json
        }
      }
    }
  }

  "Checking an applicant cannot create an account" when {

    "An applicant has no associated surname so CANNOT proceed with their application" must {

      "return with a 200 result and a response showing the surname is missing" in {
        val encodedNino = "QUUxMjAxMjNB"

        val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, userDetailsURL(encodedNino))

        checkEligibilityResponse.status shouldBe OK
        val missingUserInfos = MissingUserInfos(Set(Surname))
        checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Surname"]}}""")
      }
    }
  }


  "Checking an applicant cannot create an account" when {

    "An applicant has no associated email should be able to proceed with their application" must {

      "return with a 200 result and a response showing the email address is missing" in {
        val encodedNino = "QUUxMzAxMjNC"

        val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, userDetailsURL(encodedNino))

        checkEligibilityResponse.status shouldBe OK
        val missingUserInfos = MissingUserInfos(Set(Email))
        checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Email"]}}""")
      }
    }
  }

}