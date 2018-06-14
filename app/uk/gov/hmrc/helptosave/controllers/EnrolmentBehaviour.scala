/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnector
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentBehaviour {

  val enrolmentStore: EnrolmentStore

  val itmpConnector: ITMPEnrolmentConnector

  def setITMPFlagAndUpdateMongo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = for {
    _ ← itmpConnector.setFlag(nino)
    _ ← enrolmentStore.update(nino, itmpFlag = true)
  } yield ()

  def enrolUser(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = {
    for {
      _ ← enrolmentStore.insert(createAccountRequest.userInfo.nino,
                                itmpFlag = false,
                                createAccountRequest.eligibilityReason,
                                createAccountRequest.userInfo.registrationChannel)
      _ ← setITMPFlagAndUpdateMongo(createAccountRequest.userInfo.nino)
    } yield ()
  }
}
