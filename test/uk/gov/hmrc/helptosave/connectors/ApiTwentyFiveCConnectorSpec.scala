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

package uk.gov.hmrc.helptosave.connectors

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.models.{ApiTwentyFiveCValues, AwAwardStatus, Award}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class ApiTwentyFiveCConnectorSpec extends TestSupport with WithFakeApplication {

  val date = new LocalDate(2017, 6, 12) // scalastyle:ignore magic.number

  def mockGet(url: String)(response: HttpResponse) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, Map.empty[String, String], *, *)
      .returning(Future.successful(response))

  lazy val connector = new ApiTwentyFiveCConnectorImpl {
    override val helpToSaveStubURL = "url"
    override def serviceURL(nino: String) = nino
    override val http = mockHttp
  }

  def getAwards(nino: NINO): Either[String, List[Award]] =
    Await.result(connector.getAwards(nino).value, 5.seconds)

  "getAwards" must {
    val nino = randomNINO()

    val url = s"url/$nino"

    "return awards when there are awards to return" in {
      val expected = Award(AwAwardStatus.Open, date, date, 3, true, date)
      val response = ApiTwentyFiveCValues(nino, List(expected))

      mockGet(url)(HttpResponse(200, Some(Json.toJson(response)))) // scalastyle:ignore magic.number

      getAwards(nino) shouldBe Right(List(expected))
    }

    "return an error" when {

      def testFailure(mockActions: ⇒ Unit): Unit = {
        mockActions
        getAwards(nino).isLeft shouldBe true
      }

      "there is an error calling the API" in {
        val error = new Exception("Oh no!")
        testFailure(
          (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
            .expects(url, Map.empty[String, String], *, *)
            .returning(Future.failed(error))
        )
      }

      "the API doesn't return JSON in the response body" in {
        testFailure(
          mockGet(url)(HttpResponse(200, responseString = Some("hello?")))  // scalastyle:ignore magic.number
        )
      }

      "the API return JSON of the wrong format in the response body" in {
        testFailure(
          mockGet(url)(HttpResponse(200, Some(  // scalastyle:ignore magic.number
            Json.parse(
              """
                |{
                |  "a": "b"
                |}
              """.stripMargin)
          ))))
      }
    }
  }
}
