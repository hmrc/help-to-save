/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.MissingUserInfos

case class EligibilityCheckResult(result: Either[MissingUserInfos, Option[UserInfo]])

object EligibilityCheckResult {

  implicit val missingInfosFormat: Format[MissingUserInfos] = Json.format[MissingUserInfos]

  implicit val eligibilityResultFormat: Format[EligibilityCheckResult] = new Format[EligibilityCheckResult] {
    override def reads(json: JsValue): JsResult[EligibilityCheckResult] = {
      (json \ "result").toOption match {
        case None ⇒
          JsError("Could not find 'result' path in JSON")

        case Some(jsValue) ⇒
          jsValue.validate[MissingUserInfos].fold(e1 ⇒
            jsValue.validateOpt[UserInfo].fold (e2 ⇒
              JsError(e1 ++ e2),
              maybeUserInfo ⇒ JsSuccess(EligibilityCheckResult(Right(maybeUserInfo)))
            ),
            missing ⇒ JsSuccess(EligibilityCheckResult(Left(missing)))
          )
      }
    }

    override def writes(o: EligibilityCheckResult): JsValue = Json.obj(
      o.result.fold(
        missingInfos ⇒ "result" -> Json.toJson(missingInfos),
        maybeUserInfo ⇒ "result" -> Json.toJson(maybeUserInfo)
      ))
  }
}