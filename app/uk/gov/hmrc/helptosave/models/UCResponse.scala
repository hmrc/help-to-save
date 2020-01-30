/*
 * Copyright 2020 HM Revenue & Customs
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

case class UCResponse(ucClaimant: Boolean, withinThreshold: Option[Boolean])

object UCResponse {

  implicit val reads: Format[UCResponse] = new Format[UCResponse] {

    override def reads(json: JsValue): JsResult[UCResponse] = {

      (json \ "ucClaimant").validate[String]
        .fold(
          errors ⇒ JsError(s"unable to parse UCResponse from proxy, due to=$errors"),
          a ⇒
            (json \ "withinThreshold").validateOpt[String]
              .fold(
                errors ⇒ JsError(s"unable to parse UCResponse from proxy, due to=$errors"),
                b ⇒
                  (a, b) match {
                    case ("Y", Some("Y")) ⇒ JsSuccess(UCResponse(ucClaimant      = true, withinThreshold = Some(true)))
                    case ("Y", Some("N")) ⇒ JsSuccess(UCResponse(ucClaimant      = true, withinThreshold = Some(false)))
                    case ("N", None)      ⇒ JsSuccess(UCResponse(ucClaimant      = false, withinThreshold = None))
                    case _                ⇒ JsError(s"unable to parse UCResponse from proxy, json=$json")
                  }
              )
        )
    }

    override def writes(response: UCResponse): JsValue = {

      val (a, b) = response match {
        case UCResponse(true, Some(true))  ⇒ ("Y", Some("Y"))
        case UCResponse(true, Some(false)) ⇒ ("Y", Some("N"))
        case _                             ⇒ ("N", None)
      }

      val fields = {
        val f = List("ucClaimant" -> JsString(a))
        b.fold(f)(value ⇒ ("withinThreshold" → JsString(value)) :: f)
      }

      JsObject(fields)
    }
  }
}
