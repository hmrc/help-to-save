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

package uk.gov.hmrc.helptosaveeligibilitycheck.connectors

import java.time.LocalDate

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalamock.scalatest.MockFactory
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosaveeligibilitycheck.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosaveeligibilitycheck.models.{Address, NSIUserInfo, UserInfo}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class NSIConnectorImplSpec extends UnitSpec with WithFakeApplication with MockFactory {

  val mockHTTPPost = mock[WSPost]
  val (forename, surname) = "Tyrion" → "Lannister"
  val dateOfBirth = LocalDate.ofEpochDay(0L)
  val addressLine1 = "Casterly Rock"
  val addressLine2 = "The Westerlands"
  val addressLine3 = "Westeros"
  val postcode = "BA148FY"
  val country = "GB"
  val address = Address(Some(addressLine1), Some(addressLine2), Some(addressLine3),
    None, None, Some(postcode), Some(country))

  val nino = "WM123456C"
  val email = "tyrion_lannister@gmail.com"


  val nsiUserInfoValid = NSIUserInfo(
    forename, surname, dateOfBirth, addressLine1, addressLine2, Some(addressLine3),
    None, None, postcode, Some(country), nino, "02", None, email, "online")

  lazy val testNSAndIConnectorImpl = new NSIConnectorImpl {
    override val http = mockHTTPPost
  }

  implicit val hc = HeaderCarrier()
  implicit val ex = fakeApplication.injector.instanceOf[ExecutionContext]

  val config = fakeApplication.configuration.underlying

  val encodedAuthorisation: String = {
    val userName: String = config.getString("microservice.services.nsi.username")
    val password: String = config.getString("microservice.services.nsi.password")
    BaseEncoding.base64().encode((userName + ":" + password).getBytes(Charsets.UTF_8))
  }
  val baseUrl: String = {
    val port = config.getString("microservice.services.nsi.port")
    val host = config.getString("microservice.services.nsi.host")
    s"http://$host:$port"
  }
  val nsiUrlEnd: String = config.getString("microservice.services.nsi.url")
  val url = s"$baseUrl/$nsiUrlEnd"

  def mockCreateAccount[I](body: I)(result: HttpResponse): Unit =
    (mockHTTPPost.POST[I, HttpResponse](
      _: String, _: I, _: Seq[(String, String)]
    )(_: Writes[I], _: HttpReads[HttpResponse], _: HeaderCarrier))
      .expects(url, body, Seq(("Authorization", encodedAuthorisation)), *, *, *)
      .returning(Future.successful(result))

  "the createAccount Method" must {
    "Return a SubmissionSuccess when the status is Created" in {
      mockCreateAccount(nsiUserInfoValid)(HttpResponse(Status.CREATED))
      val result = testNSAndIConnectorImpl.createAccount(nsiUserInfoValid)
      Await.result(result, 3.seconds) shouldBe SubmissionSuccess
    }

    "Return a SubmissionFailure when the status is BAD_REQUEST" in {
      val submissionFailure = SubmissionFailure(None, "I am a error message", "I am a errorDetail")
      mockCreateAccount(nsiUserInfoValid)(HttpResponse(Status.BAD_REQUEST,
        Some(Json.toJson(submissionFailure))))
      val result = testNSAndIConnectorImpl.createAccount(nsiUserInfoValid)
      Await.result(result, 3.seconds) shouldBe submissionFailure
    }

    "Return a SubmissionFailure when the status is anything else" in {
      val submissionFailure = SubmissionFailure(None, s"Bad Status", Status.BAD_GATEWAY.toString)
      mockCreateAccount(nsiUserInfoValid)(HttpResponse(Status.BAD_GATEWAY))
      val result = testNSAndIConnectorImpl.createAccount(nsiUserInfoValid)
      Await.result(result, 3.seconds) shouldBe submissionFailure
    }
  }

}
