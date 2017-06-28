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

package uk.gov.hmrc.helptosave.services

import uk.gov.hmrc.helptosave.connectors.{EligibilityCheckConnector, EligibilityResult}
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityCheckServiceSpec extends TestSupport {

  val mockEligibilityConnector = mock[EligibilityCheckConnector]
  val checkerService = new EligibilityCheckService(mockEligibilityConnector)
  val eligibleNino = "AE123456C"
  val nonEligibleNino = "QQ123456C"

  def mockEligibilityResult(nino: String)(result: EligibilityResult): Unit = {
    (mockEligibilityConnector.isEligible(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(Result(Future.successful(result)))
  }

  "EligibilityCheckerService " must {
    "return true when the user is eligible " in {
      mockEligibilityResult(eligibleNino)(EligibilityResult(true))
      val result = checkerService.isEligible(eligibleNino)
      Await.result(result.value, 3.seconds).fold(_ ⇒ false, identity) shouldBe true
    }

  }
}

