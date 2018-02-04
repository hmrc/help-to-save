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

import org.scalamock.handlers.CallHandler4
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.{AuthProviders, Enrolment, Enrolments}
import uk.gov.hmrc.helptosave.config.HtsAuthConnector
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait StrideAuthSupport extends TestSupport {

  lazy val roles: List[String] = fakeApplication.configuration.underlying.getStringList("stride.roles").asScala.toList

  val mockAuthConnector: HtsAuthConnector = mock[HtsAuthConnector]

  def mockAuthorised[A](expectedPredicate: Predicate,
                        expectedRetrieval: Retrieval[A])(result: Either[Throwable, A]): CallHandler4[Predicate, Retrieval[A], HeaderCarrier, ExecutionContext, Future[A]] =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedPredicate, expectedRetrieval, *, *)
      .returning(result.fold(Future.failed, Future.successful))

  def mockSuccessfulAuthorisation(): CallHandler4[Predicate, Retrieval[Enrolments], HeaderCarrier, ExecutionContext, Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(roles.map(Enrolment(_)).toSet)))
}
