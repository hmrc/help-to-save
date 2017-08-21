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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Writes
import uk.gov.hmrc.play.test.WithFakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnectorImpl.PostBody
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class ITMPEnrolmentConnectorImplSpec extends TestSupport with WithFakeApplication with GeneratorDrivenPropertyChecks{

  def mockPost[A](url: String, body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp.post(_: String, _: A, _: Seq[(String,String)])(_: Writes[A], _: HeaderCarrier))
      .expects(url, body, Seq.empty[(String,String)], *, *)
      .returning(result.fold[Future[HttpResponse]](Future.failed(new Exception("")))(Future.successful))


  lazy val connector = new ITMPEnrolmentConnectorImpl{
    override val http = mockHttp
  }

  "The ITMPConnectorImpl" when {

    val nino = "NINO"

    def url(nino: NINO): String = s"${connector.itmpEnrolmentURL}/set-enrolment-flag/$nino"

    "setting the ITMP flag" must {

      "perform a post to the configured URL" in {
        mockPost(url(nino), PostBody())(None)

        await(connector.setFlag(nino).value)
      }

      "return a Right if the call to ITMP comes back with a 200 (OK) status" in {
        mockPost(url(nino), PostBody())(Some(HttpResponse(200)))

        await(connector.setFlag(nino).value) shouldBe Right(())
      }

      "return a Right if the call to ITMP comes back with a 409 (CONFLICT) status" in {
        mockPost(url(nino), PostBody())(Some(HttpResponse(409)))

        await(connector.setFlag(nino).value) shouldBe Right(())
      }

      "return a Left" when {

        "the call to ITMP comes back with a status which isn't 200 or 409" in {
          forAll{ status: Int ⇒
            whenever(status != 200 && status != 409){
              mockPost(url(nino), PostBody())(Some(HttpResponse(status)))

              await(connector.setFlag(nino).value).isLeft shouldBe true
            }
          }
        }

        "an error occurs while calling the ITMP endpoint" in {
          mockPost(url(nino), PostBody())(None)
          await(connector.setFlag(nino).value).isLeft shouldBe true
        }

      }

    }

  }
}
