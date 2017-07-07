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

import scala.util.Try

case class EligibilityCheckResult(result: Either[MissingUserInfos, Option[UserInfo]])

object EligibilityCheckResult {

  implicit val eligibilityResultFormat: Format[EligibilityCheckResult] = new Format[EligibilityCheckResult] {

    override def reads(json: JsValue): JsResult[EligibilityCheckResult] = {

      def readUserInfo(result: JsLookupResult): Either[String, EligibilityCheckResult] = {

        result.get match {
          case maybeUser: JsObject ⇒
            maybeUser.asOpt[UserInfo] match {
              case Some(userInfo) ⇒ Right(EligibilityCheckResult(Right(Some(userInfo))))
              case _ ⇒ Left("Invalid Json for reading UserInfo")
            }
          case _ ⇒ Right(EligibilityCheckResult(Right(None)))
        }
      }

      val mayBeEligCheckResult =
        Try(json \ "result").toOption.flatMap(
          result ⇒ {
            val elig = result.get.asOpt[MissingUserInfos].fold(
              readUserInfo(result)
            )(missingInfos ⇒ Right(EligibilityCheckResult(Left(missingInfos))))

            Some(elig)
          }
        )

      mayBeEligCheckResult match {
        case Some(Right(eligResult)) ⇒ JsSuccess(eligResult)
        case _ ⇒ JsError("Invalid EligibilityCheckResult Json")
      }
    }

    override def writes(o: EligibilityCheckResult): JsValue = Json.obj(
      o.result.fold(
        missingInfos ⇒ "result" -> Json.toJson(missingInfos),
        maybeUserInfo ⇒ "result" -> Json.toJson(maybeUserInfo)
      ))
  }
}