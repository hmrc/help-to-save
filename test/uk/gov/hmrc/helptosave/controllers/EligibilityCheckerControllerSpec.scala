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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.{Result ⇒ PlayResult}
import uk.gov.hmrc.helptosave.connectors.EligibilityCheckConnector
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class EligibilityCheckerControllerSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  class TestApparatus {
    val connector = mock[EligibilityCheckConnector]

    def doRequest(nino: String,
                  controller: EligibilityCheckController): Future[PlayResult] =
      controller.eligibilityCheck(nino)(FakeRequest())

    def mockEligibilityCheckerService(nino: NINO)(result: Option[EligibilityCheckResult]): Unit =
      (connector.isEligible(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.fromOption[Future](result, "mocking failed eligibility check"))

    val controller = new EligibilityCheckController(connector)
  }

  "The EligibilityCheckerController" when {

    "handling requests to perform eligibility checks" must {

      val userDetailsURI = "user-details-uri"
      val nino = randomNINO()

      def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

      "ask the EligibilityCheckerService if the user is eligible and return the result" in new TestApparatus {
        mockEligibilityCheckerService(nino)(None)
        await(doRequest(nino, controller))
      }


      "return with a status 500 if the eligibility check service fails" in new TestApparatus {
        mockEligibilityCheckerService(nino)(None)

        val result = doRequest(nino, controller)
        status(result) shouldBe 500
      }
    }
  }

}
