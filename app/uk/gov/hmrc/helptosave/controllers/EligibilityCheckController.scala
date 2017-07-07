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

import cats.data.{EitherT, ValidatedNel}
import cats.instances.future._
import cats.syntax.cartesian._
import cats.syntax.option._
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.connectors.EligibilityCheckConnector
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.services.UserInfoAPIService
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext


class EligibilityCheckController @Inject()(eligCheckConnector: EligibilityCheckConnector,
                                           userInfoAPIService: UserInfoAPIService)(implicit ec: ExecutionContext) extends BaseController {

  def eligibilityCheck(nino: NINO, oauthAuthorisationCode: String): Action[AnyContent] = Action.async { implicit request ⇒
    val result: Result[Option[OpenIDConnectUserInfo]] =
      eligCheckConnector.isEligible(nino).flatMap { isEligible ⇒
        if (isEligible) {
          userInfoAPIService.getUserInfo(oauthAuthorisationCode, nino).map(Some(_))
        } else {
          EitherT.pure(None)
        }
      }

    result.fold(
      // there was an error calling the services above
      error ⇒ {
        Logger.error(s"Could not perform eligibility check: $error")
        InternalServerError(error)
      },
      _.fold(
        // the user is ineligible
        Ok(Json.toJson(EligibilityCheckResult(Right(None))))
      ) { apiUserInfo ⇒
        // the user is eligible
        toUserInfo(apiUserInfo, nino).fold(
          { e ⇒
            // the api user info couldn't be converted to user info
            val missingInfos = MissingUserInfos(e.toList.to[Set])
            Logger.error(s"user $nino has missing information: ${missingInfos.missingInfo.mkString(",")}")
            Ok(Json.toJson(EligibilityCheckResult(Left(missingInfos))))
          },
          // the api user info was successfully converted to user info
          userInfo ⇒ Ok(Json.toJson(EligibilityCheckResult(Right(Some(userInfo))))))
      }
    )
  }

  private def toUserInfo(apiUserInfo: OpenIDConnectUserInfo, nino: NINO): ValidatedNel[MissingUserInfo, UserInfo] = {
    val firstNameCheck = apiUserInfo.given_name.toValidNel[MissingUserInfo](GivenName)
    val lastNameCheck = apiUserInfo.family_name.toValidNel(Surname)
    val birthDateCheck = apiUserInfo.birthdate.toValidNel(DateOfBirth)
    val emailCheck = apiUserInfo.email.toValidNel(Email)
    val addressCheck = apiUserInfo.address.toValidNel(Contact)

    (firstNameCheck |@| lastNameCheck |@| birthDateCheck |@| emailCheck |@| addressCheck).map {
      case (firstName, surname, dob, email, address) ⇒
        UserInfo(
          firstName,
          surname,
          nino,
          dob,
          email,
          Address(
            address.formatted.split("\n").toList,
            address.postal_code,
            // user info API returns ISO 3166-2 codes: the first two characters of it
            // is the ISO 3166-1 alpha-2 code that we want (see https://en.wikipedia.org/wiki/ISO_3166-2)
            address.code.map(_.take(2)))
        )
    }
  }

}


