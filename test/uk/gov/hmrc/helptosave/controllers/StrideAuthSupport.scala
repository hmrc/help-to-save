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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.{AuthProviders, Enrolment, Enrolments}

import java.util.Base64
import scala.concurrent.Future
import org.mockito.stubbing.OngoingStubbing

trait StrideAuthSupport extends AuthSupport {
  lazy val roles: Seq[String] =
    fakeApplication.configuration
      .get[Seq[String]]("stride.base64-encoded-roles")
      .map(s => new String(Base64.getDecoder.decode(s)))

  def mockAuthorised[A](expectedPredicate: Predicate, expectedRetrieval: Retrieval[A])(
    result: Either[Throwable, A]
  ): OngoingStubbing[Future[A]] =
    when(mockAuthConnector.authorise(eqTo(expectedPredicate), eqTo(expectedRetrieval))(any(), any()))
      .thenAnswer(_ => result.fold(Future.failed, Future.successful))

  def mockSuccessfulAuthorisation(): OngoingStubbing[Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(roles.map(Enrolment(_)).toSet))
    )
}
