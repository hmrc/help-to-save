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

import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport extends TestSupport {

  val NinoWithCL200: Enrolment = Enrolment("HMRC-NI").withConfidenceLevel(L200)

  val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  val AuthWithCL200: Predicate = NinoWithCL200 and AuthProvider

  val nino = "AE123456C"

  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", L200)

  val enrolments = Enrolments(Set(enrolment))

  val mockAuthConnector: HtsAuthConnector = mock[HtsAuthConnector]

  def mockAuthResultWithFail(predicate: Predicate)(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthResultWithSuccess(predicate: Predicate)(result: Enrolments) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, Retrievals.authorisedEnrolments, *, *)
      .returning(Future.successful(result))

}

