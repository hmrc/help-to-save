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
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.GGAndPrivilegedProviders
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.concurrent.Future

trait AuthSupport extends TestSupport {

  val nino = "AE123456C"

  val mockedNinoRetrieval: Some[String] = Some(nino)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuth[A](predicate: Predicate, retrieval: Retrieval[A])(
    result: Either[Exception, A]
  ): OngoingStubbing[Future[A]] =
    when(mockAuthConnector.authorise(eqTo(predicate), eqTo(retrieval))(any(), any()))
      .thenAnswer(_ => result.fold(e => Future.failed[A](e), r => Future.successful(r)))

  def mockAuth[A](retrieval: Retrieval[A])(result: Either[Exception, A]): OngoingStubbing[Future[A]] =
    when(mockAuthConnector.authorise(any(), eqTo(retrieval))(any(), any()))
      .thenAnswer(_ => result.fold(e => Future.failed[A](e), r => Future.successful(r)))

  def testWithGGAndPrivilegedAccess(f: (() => Unit) => Unit): Unit = {
    withClue("For GG access: ") {
      f { () =>
        mockAuth(GGAndPrivilegedProviders, v2.Retrievals.authProviderId)(Right(GGCredId("id")))
        mockAuth(EmptyPredicate, v2.Retrievals.nino)(Right(Some(nino)))
      }
    }

    withClue("For privileged access: ") {
      f { () =>
        mockAuth(GGAndPrivilegedProviders, v2.Retrievals.authProviderId)(Right(PAClientId("id")))
      }
    }
  }

}
