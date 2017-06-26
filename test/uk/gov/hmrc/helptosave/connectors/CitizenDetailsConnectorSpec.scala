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

import java.time.LocalDate

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.{CitizenDetailsAddress, CitizenDetailsPerson, CitizenDetailsResponse}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.WithFakeApplication
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global


class CitizenDetailsConnectorSpec extends WordSpec with WithFakeApplication with Matchers with MockFactory{

  implicit val hc = HeaderCarrier()

  val mockHttp = mock[WSHttp]

  def mockGet(url: String)(response: HttpResponse) =
    (mockHttp.get(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, Map.empty[String, String], *, *)
      .returning(Future.successful(response))


  lazy val connector = new CitizenDetailsConnectorImpl {
    override val citizenDetailsBaseURL = "url"

    override val http = mockHttp
  }

  def getDetails(nino: NINO): Either[String, CitizenDetailsResponse] =
    Await.result(connector.getDetails(nino).value, 5.seconds)


  "getDetails" must {

    val nino = "nino"
    lazy val url = connector.citizenDetailsURI(nino)

    "return details when there are details to return" in {
      val date = LocalDate.of(1999, 6, 1) // scalastyle:ignore magic.number
      val person = CitizenDetailsPerson(Some("fname"), Some("lname"), Option(date))
      val address = CitizenDetailsAddress(Some("line1"), Some("line2"), Some("line3"), None, None, None, None)
      val expected = CitizenDetailsResponse(Some(person), Some(address))
      mockGet(url)(HttpResponse(200, Some(Json.toJson(expected))))

      getDetails(nino) shouldBe Right(expected)
    }

    "return an error" when {

      def testFailure(mockActions: ⇒ Unit): Unit = {
        mockActions
        getDetails(nino).isLeft shouldBe true
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
          mockGet(url)(HttpResponse(200, responseString = Some("hello")))//scalastyle:ignore magic.number
        )
      }

      "the API return JSON of the wrong format in the response body" ignore {
        // TODO: currently the following test fails - since all of the fields
        // TODO: are optional, the JSON gets converted to UserInfo with all
        // TODO: the fields set to None
        testFailure(
          mockGet(url)(HttpResponse(200, Some(  // scalastyle:ignore magic.number
            Json.parse(
              """
                |{
                |  "a": "b"
                |}
              """.stripMargin)
          )))
        )
      }
    }
  }

}
