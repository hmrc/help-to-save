/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models

import uk.gov.hmrc.auth.core.retrieve._

sealed trait AccessType

object AccessType {

  case object StrideAccess extends AccessType

  case object GGAccess extends AccessType

  def fromLegacyCredentials(credentials: LegacyCredentials): Either[String, AccessType] = credentials match {
    case GGCredId(_)   ⇒ Right(GGAccess)
    case PAClientId(_) ⇒ Right(StrideAccess)
    case other         ⇒ Left(s"Unsupported credential type: $other")
  }

}
