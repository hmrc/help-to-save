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

package uk.gov.hmrc.helptosave.actors

import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatest.EitherValues
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.helptosave.utils.{MockPagerDuty, TestSupport}

import scala.concurrent.Future

class UCThresholdConnectorProxyActorSpec
    extends TestSupport with IdiomaticMockito with MockPagerDuty with EitherValues {
  val connector = mock[DESConnector]

  def mockConnectorGetValue(response: Double): ScalaOngoingStubbing[Future[Double]] =
    connector
      .getThreshold()(*, *)
      .returns(toFuture(response))

  "The UCThresholdConnectorProxyActor" when {

    "asked for the threshold value" must {

      "ask for and return the value from the threshold connector" in {

        connector
          .getThreshold()(*, *)
          .returns(toFuture(100.0))
      }
    }
  }
}
