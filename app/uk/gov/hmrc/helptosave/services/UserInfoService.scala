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
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosave.connectors.{CitizenDetailsConnector, UserDetailsConnector}
import uk.gov.hmrc.helptosave.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosave.models.{Address, UserInfo}
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


class UserInfoService @Inject()(userDetailsConnector: UserDetailsConnector,
                                citizenDetailsConnector: CitizenDetailsConnector) {

  def getUserInfo(userDetailsUri: String, nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserInfo] =
    for {
      userDetails ← userDetailsConnector.getUserDetails(userDetailsUri)
      citizenDetails ← citizenDetailsConnector.getDetails(nino)
      userInfo ← EitherT.fromEither[Future](toUserInfo(userDetails, citizenDetails, nino).toEither).leftMap(
        errors ⇒ s"Could not create user info: ${errors.toList.mkString(",")}")
    } yield userInfo

  private def toUserInfo(u: UserDetailsResponse,
                         c: CitizenDetailsResponse,
                         nino: NINO): ValidatedNel[String, UserInfo] = {
    val surnameValidation =
      u.lastName.orElse(c.person.flatMap(_.lastName))
        .toValidNel("Could not find last name")

    val dateOfBirthValidation =
      u.dateOfBirth.orElse(c.person.flatMap(_.dateOfBirth))
        .toValidNel("Could not find date of birth")

    val emailValidation = u.email.toValidNel("Could not find email address")

    val addressValidation = c.address.toValidNel("Could not find address")

    (surnameValidation |@| dateOfBirthValidation |@| emailValidation |@| addressValidation)
      .map((surname, dateOfBirth, email, address) ⇒
        UserInfo(u.name, surname, nino, dateOfBirth, email, Address(address))
      )
  }

}
