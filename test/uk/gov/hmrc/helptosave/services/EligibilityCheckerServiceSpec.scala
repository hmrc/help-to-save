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

import org.joda.time.LocalDate
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.helptosave.connectors.ApiTwentyFiveCConnector
import uk.gov.hmrc.helptosave.models.{AwAwardStatus, Award}
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class EligibilityCheckerServiceSpec
  extends UnitSpec with MockFactory {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockEligibilityConnector = mock[ApiTwentyFiveCConnector]
  val checkerService = new EligibilityCheckerService(mockEligibilityConnector)
  val todayDate = LocalDate.parse("2017-08-24")
  val validAward = Award(AwAwardStatus.Provisional, LocalDate.parse("2014-04-17"), LocalDate.parse("2014-10-03"), 6000, true, LocalDate.now().plusDays(1))
  val fakeNino = "WM123456C"

  def mockEligibilityResult(nino: String)(result: List[Award]): Unit = {
    (mockEligibilityConnector.getAwards(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(Result(Future.successful(result)))
  }

  def isEligible(result: Result[Boolean]): Boolean =
    Await.result(result.value, 3.seconds).fold(_ ⇒ false, identity)

  "EligibilityCheckerService getValidAwardsDate"
  "return true valid awards " in {
    mockEligibilityResult(fakeNino)(List(validAward))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe true
  }
  "return true if  av_end_date is today " in {
    mockEligibilityResult(fakeNino)(List(validAward.copy(av_end_date = LocalDate.now())))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe true
  }
  "return false is there is no awards that have an av_end_date that is not today or tommoro" in {
    mockEligibilityResult(fakeNino)(List(validAward.copy(av_end_date = LocalDate.now().minusDays(1))))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe false
  }
  "return false if aw_award_status is not Provisional or Finalised" in {
    mockEligibilityResult(fakeNino)(List(validAward.copy(aw_award_status = AwAwardStatus.Deleted)))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe false
  }
  "return false if ae_etc1_wtc_entitlement is not true" in {
    mockEligibilityResult(fakeNino)(List(validAward.copy(ae_etc1_wtc_entitlement = false)))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe false
  }
  "return false if house hold income is less then zero " in {
    mockEligibilityResult(fakeNino)(List(validAward.copy(av_total_taper_household_award = -5)))
    val result = checkerService.getEligibility(fakeNino)
    isEligible(result) shouldBe false
  }
}

