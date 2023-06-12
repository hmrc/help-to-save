/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.instances.string._
import cats.syntax.eq._
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentBehaviour {

  val enrolmentStore: EnrolmentStore

  val helpToSaveService: HelpToSaveService

  def setITMPFlagAndUpdateMongo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = for {
    _ <- helpToSaveService.setFlag(nino)
    _ <- enrolmentStore.updateItmpFlag(nino, itmpFlag = true)
  } yield ()

  def setAccountNumber(nino: NINO, accountNumber: String)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] =
    enrolmentStore.updateWithAccountNumber(nino, accountNumber)

  def enrolUser(createAccountRequest: CreateAccountRequest, accountNumber: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = {
    if (createAccountRequest.source === "Stride-Manual") { //HTS-1403: set the itmpFlag to true in mongo straightaway without actually calling ITMP
      enrolmentStore.insert(createAccountRequest.payload.nino,
                            itmpFlag = true,
                            createAccountRequest.eligibilityReason,
                            createAccountRequest.source,
                            accountNumber)
    } else {
      for {
        _ <- enrolmentStore.insert(createAccountRequest.payload.nino,
                                   itmpFlag = false,
                                   createAccountRequest.eligibilityReason,
                                   createAccountRequest.source,
                                   accountNumber)
        _ <- setITMPFlagAndUpdateMongo(createAccountRequest.payload.nino)
      } yield ()
    }
  }
}
