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

import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport extends TestSupport {

  val nino = "AE123456C"

  val mockedNinoRetrieval = Some(nino)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthResultWithFail(predicate: Predicate)(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthResultWithSuccess(predicate: Predicate)(result: Option[String]) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, Retrievals.nino, *, *)
      .returning(Future.successful(result))

  def mockAuthResultNoRetrievals(predicate: Predicate) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[_])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, EmptyRetrieval, *, *)
      .returning(Future.successful(()))
}

