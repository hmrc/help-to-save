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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import uk.gov.hmrc.helptosave.connectors.ApiTwentyFiveCConnector
import uk.gov.hmrc.helptosave.models.AwAwardStatus.{Finalised, Provisional}
import uk.gov.hmrc.helptosave.models.Award
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class EligibilityCheckerService @Inject()(apiTwentyFiveCConnector: ApiTwentyFiveCConnector) {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Boolean] =
    apiTwentyFiveCConnector.getAwards(nino).map(awards ⇒ checkEligibilityForHelpToSave(awards, LocalDate.now()))

  private def checkEligibilityForHelpToSave(awards: List[Award], today: LocalDate): Boolean = {
    val validAwards =  awards.filter(award ⇒ isEndDateTodayOrAfter(award.av_end_date, today))
    validAwards.exists(impliesEligible)
  }

  private def impliesEligible(award: Award): Boolean = award match {
    case Award(Provisional, _, _, householdIncome, true, _) if householdIncome > 0  ⇒ true
    case Award(Finalised, _, _, householdIncome, true, _) if householdIncome > 0  ⇒ true
    case _ ⇒ false
  }

  private def isEndDateTodayOrAfter(endDate: LocalDate, today: LocalDate): Boolean =
    endDate.isAfter(today) || endDate.isEqual(today)



}

