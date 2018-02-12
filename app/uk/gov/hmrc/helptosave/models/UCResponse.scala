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

package uk.gov.hmrc.helptosave.models

import play.api.libs.json._

case class UCResponse(ucClaimant: Boolean, withinThreshold: Boolean)

object UCResponse {

  implicit val reads: Format[UCResponse] = new Format[UCResponse] {

    override def reads(json: JsValue): JsResult[UCResponse] = {
      ((json \ "ucClaimant").as[String], (json \ "withinThreshold").asOpt[String]) match {
        case ("Y", Some("Y")) ⇒ JsSuccess(UCResponse(ucClaimant      = true, withinThreshold = true))
        case ("Y", Some("N")) ⇒ JsSuccess(UCResponse(ucClaimant      = true, withinThreshold = false))
        case ("N", _)         ⇒ JsSuccess(UCResponse(ucClaimant      = false, withinThreshold = false))
        case _                ⇒ JsError(s"unable to parse UCResponse from proxy, json=$json")
      }
    }

    override def writes(response: UCResponse): JsValue = {

      val (a, b) = response match {
        case UCResponse(true, true)  ⇒ ("Y", "Y")
        case UCResponse(true, false) ⇒ ("Y", "N")
        case _                       ⇒ ("N", "N")
      }

      JsObject(List("ucClaimant" -> JsString(a), "withinThreshold" -> JsString(b)))
    }
  }
}
