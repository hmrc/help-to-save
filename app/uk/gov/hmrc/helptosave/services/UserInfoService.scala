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

package uk.gov.hmrc.helptosave.services

import cats.data.{EitherT, ValidatedNel}
import cats.instances.future._
import cats.syntax.cartesian._
import cats.syntax.option._
import com.google.inject.Inject
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosave.connectors.{CitizenDetailsConnector, UserDetailsConnector}
import uk.gov.hmrc.helptosave.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosave.models.{Address, MissingUserInfo, UserInfo}
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.{CitizenDetailsError, MissingUserInfos, UserDetailsError}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UserInfoService @Inject()(userDetailsConnector: UserDetailsConnector,
                                citizenDetailsConnector: CitizenDetailsConnector) {

  def getUserInfo(userDetailsUri: String, nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future,UserInfoServiceError, UserInfo] = for {
      userDetails ← userDetailsConnector.getUserDetails(userDetailsUri).leftMap(UserDetailsError)
      citizenDetails ← citizenDetailsConnector.getDetails(nino).leftMap(CitizenDetailsError)
      userInfo ← EitherT.fromEither[Future](toUserInfo(userDetails, citizenDetails, nino))
    } yield userInfo

  private def toUserInfo(u: UserDetailsResponse,
                         c: CitizenDetailsResponse,
                         nino: NINO): Either[UserInfoServiceError, UserInfo] = {
    val surnameValidation: ValidatedNel[MissingUserInfo, String] =
      u.lastName.orElse(c.person.flatMap(_.lastName))
        .toValidNel(MissingUserInfo.Surname)

    val dateOfBirthValidation =
      u.dateOfBirth.orElse(c.person.flatMap(_.dateOfBirth))
        .toValidNel(MissingUserInfo.DateOfBirth)

    val emailValidation = u.email.toValidNel(MissingUserInfo.Email)

    val addressValidation = c.address.toValidNel(MissingUserInfo.Contact)

    val validation: ValidatedNel[MissingUserInfo, UserInfo] = (surnameValidation |@| dateOfBirthValidation |@| emailValidation |@| addressValidation)
      .map((surname, dateOfBirth, email, address) ⇒
        UserInfo(u.name, surname, nino, dateOfBirth, email, Address(address)))

    validation.leftMap(m ⇒ MissingUserInfos(m.toList.toSet): UserInfoServiceError).toEither
  }

}

object UserInfoService {
  sealed trait UserInfoServiceError

  object UserInfoServiceError {
    case class UserDetailsError(message: String) extends UserInfoServiceError
    case class CitizenDetailsError(message: String) extends UserInfoServiceError
    case class MissingUserInfos(missingInfo: Set[MissingUserInfo])  extends UserInfoServiceError

    object MissingUserInfos {
      implicit val format: Format[MissingUserInfos] = Json.format[MissingUserInfos]
    }
  }

}
