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

package uk.gov.hmrc.helptosave.util

import cats.instances.string._
import cats.syntax.eq._
import uk.gov.hmrc.http.HeaderCarrier

object HeaderCarrierOps {

  def getApiCorrelationId(implicit hc: HeaderCarrier, correlationIdHeaderName: String): Option[String] =
    hc.headers.find(p ⇒ p._1 === correlationIdHeaderName).map(_._2)
}
