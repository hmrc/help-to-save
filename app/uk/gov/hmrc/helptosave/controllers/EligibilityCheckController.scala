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

package uk.gov.hmrc.helptosave.controllers

import java.net.URLDecoder

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.models.{Address, EligibilityCheckResult, OpenIDConnectUserInfo, UserInfo}
import uk.gov.hmrc.helptosave.services.{EligibilityCheckService, UserInfoAPIService, UserInfoService}
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}


class EligibilityCheckController @Inject()(eligibilityCheckService: EligibilityCheckService,
                                           userInfoAPIService: UserInfoAPIService,
                                           userInfoService: UserInfoService)(implicit ec: ExecutionContext) extends BaseController {

  def eligibilityCheck(nino: NINO, userDetailsURI: String, oauthAuthorisationCode: String): Action[AnyContent] = Action.async { implicit request ⇒
    val result: Result[Option[UserInfo]] =
      eligibilityCheckService.isEligible(nino).flatMap { isEligible ⇒
        if (isEligible) {
          val urlDecoded = URLDecoder.decode(userDetailsURI, "UTF-8")
          getUserInfo(urlDecoded, oauthAuthorisationCode, nino).map(Some(_))
        } else {
          EitherT.pure[Future, String, Option[UserInfo]](None)
        }
      }

    result.fold(
      error ⇒ {
        Logger.error(s"Could not perform eligibility check: $error")
        InternalServerError(error)
      },
      userInfo ⇒ Ok(Json.toJson(EligibilityCheckResult(userInfo)))
    )
  }

  private def getUserInfo(userDetailsURI: String, oauthAuthorisationCode: String, nino: NINO)(implicit hc: HeaderCarrier): Result[UserInfo] =
    for {
      apiUserInfo ← userInfoAPIService.getUserInfo(oauthAuthorisationCode, nino)
      userInfo ← userInfoService.getUserInfo(userDetailsURI, nino)
    } yield combine(apiUserInfo, userInfo, nino)


  private def combine(apiUserInfo: OpenIDConnectUserInfo, userInfo: UserInfo, nino: NINO): UserInfo = {
    // find out if any of the fields we are interested in from the user
    // info API came back empty
    val empty = List(
      "given_name" → apiUserInfo.given_name.filter(_.nonEmpty),
      "family_name" → apiUserInfo.family_name.filter(_.nonEmpty),
      "birthdate" → apiUserInfo.birthdate.map(_.toString),
      "address" → apiUserInfo.address.map(_.toString),
      "address.formatted" → apiUserInfo.address.map(_.formatted).filter(_.nonEmpty),
      "address.postal_code" → apiUserInfo.address.flatMap(_.postal_code).filter(_.nonEmpty),
      "email" → apiUserInfo.email.filter(_.nonEmpty)
    ).collect{ case (k, None) ⇒ k }

    if(empty.nonEmpty){
      Logger.warn(s"User info API returned empty fields: ${empty.mkString(",")} for NINO $nino")
    }

    UserInfo(
      apiUserInfo.given_name.getOrElse(userInfo.forename),
      apiUserInfo.family_name.getOrElse(userInfo.surname),
      nino,
      apiUserInfo.birthdate.getOrElse(userInfo.dateOfBirth),
      apiUserInfo.email.getOrElse(userInfo.email),
      apiUserInfo.address.map{ a ⇒
        Address(
          a.formatted.split("\n").toList,
          a.postal_code,
          None)   // can't use country from user info API or the other sources - they aren't ISO country codes
      }.getOrElse(userInfo.address.copy(country = None))
    )
  }

}


