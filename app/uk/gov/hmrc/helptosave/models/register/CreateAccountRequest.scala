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

package uk.gov.hmrc.helptosave.models.register

import play.api.libs.json.{Json, Reads, Writes}
import uk.gov.hmrc.helptosave.models.NSIPayload

case class CreateAccountRequest(payload: NSIPayload, eligibilityReason: Option[Int], source: String, detailsManuallyEntered: Boolean)

object CreateAccountRequest {
  implicit val createAccountRequestWrites: Writes[CreateAccountRequest] = Json.writes[CreateAccountRequest]

  def createAccountRequestReads(version: Option[String]): Reads[CreateAccountRequest] = Reads[CreateAccountRequest]{ jsValue =>
    for {
      nsiPayload <- (jsValue \ "payload").validate[NSIPayload](NSIPayload.nsiPayloadReads(version))
      reason <- (jsValue \ "eligibilityReason").validateOpt[Int]
      source <- (jsValue \ "source").validate[String]
      detailsManuallyEntered <- (jsValue \ "detailsManuallyEntered").validateOpt[Boolean]
    } yield CreateAccountRequest(nsiPayload, reason, source, detailsManuallyEntered.getOrElse(false))

  }

}
