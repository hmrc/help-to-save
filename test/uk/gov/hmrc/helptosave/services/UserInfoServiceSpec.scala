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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.{CitizenDetailsPerson, CitizenDetailsResponse}
import uk.gov.hmrc.helptosave.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosave.connectors.{CitizenDetailsConnector, UserDetailsConnector}
import uk.gov.hmrc.helptosave.models.{Address, MissingUserInfo, UserInfo}
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class UserInfoServiceSpec extends TestSupport {

  val userDetailsConnector = mock[UserDetailsConnector]
  val citizenDetailsConnector = mock[CitizenDetailsConnector]

  def mockUserDetails(userDetailsURI: String)(response: Option[UserDetailsResponse]) =
    (userDetailsConnector.getUserDetails(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(userDetailsURI, *, *)
      .returning(EitherT.fromOption[Future](response, "Uh oh"))

  def mockCitizenDetails(nino: NINO)(response: Option[CitizenDetailsResponse]) =
    (citizenDetailsConnector.getDetails(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromOption[Future](response, "Oh no"))

  val service = new UserInfoService(userDetailsConnector, citizenDetailsConnector)

  def getUserInfo(userDetailsURI: String, nino: NINO): Either[UserInfoService.UserInfoServiceError, UserInfo] =
    Await.result(service.getUserInfo(userDetailsURI, nino).value, 5.seconds)

  "The UserInfoService" when {

    val userDetailsURI = "uri"
    val nino = randomNINO()

    object UserDetails {
      val name = "name1"
      val surname = "surname1"
      val email = "email"
      val dateOfBirth = LocalDate.ofEpochDay(0L)
    }

    object CitizenDetails {
      val name = "name2"
      val surname = "surname2"
      val dateOfBirth = LocalDate.ofEpochDay(1L)
      val cdAddress = CitizenDetailsConnector.CitizenDetailsAddress(Some("address"), None, None, None, None, Some("postcode"), Some("GB"))
      val address = Address(List("address"), Some("postcode"), Some("GB"))
      val person = CitizenDetailsPerson(Some(name), Some(surname), Some(dateOfBirth))
    }

    val userDetailsResponse = UserDetailsResponse(
      UserDetails.name,
      Some(UserDetails.surname),
      Some(UserDetails.email),
      Some(UserDetails.dateOfBirth)
    )

    val citizenDetailsResponse = CitizenDetailsResponse(
      Some(CitizenDetails.person),
      Some(CitizenDetails.cdAddress)
    )

    "getting user info" must {

      "use the user details connector to get user info" in {
        mockUserDetails(userDetailsURI)(None)
        getUserInfo(userDetailsURI, nino)
      }

      "use the citizen details connector to get user info" in {
        inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse))
          mockCitizenDetails(nino)(None)
        }
        getUserInfo(userDetailsURI, nino)
      }

      "combine the user details info with the citizen details when it is valid and return it" in {
        // test when user details has everything
        inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse))
        }
        getUserInfo(userDetailsURI, nino) shouldBe Right(
          UserInfo(
            UserDetails.name,
            UserDetails.surname,
            nino,
            UserDetails.dateOfBirth,
            UserDetails.email,
            CitizenDetails.address
          )
        )

        // test when user details is missing surname
        inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(lastName = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse))
        }
        getUserInfo(userDetailsURI, nino) shouldBe Right(
          UserInfo(
            UserDetails.name,
            CitizenDetails.surname,
            nino,
            UserDetails.dateOfBirth,
            UserDetails.email,
            CitizenDetails.address
          )
        )

        // test when user details is DOB
        inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(dateOfBirth = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse))
        }
        getUserInfo(userDetailsURI, nino) shouldBe Right(
          UserInfo(
            UserDetails.name,
            UserDetails.surname,
            nino,
            CitizenDetails.dateOfBirth,
            UserDetails.email,
            CitizenDetails.address
          )
        )
      }

    }

    "return an error" when {

        def testFailure(mockActions: ⇒ Unit, errorCheck: UserInfoServiceError ⇒ Unit): Unit = {
          mockActions
          getUserInfo(userDetailsURI, nino).isLeft shouldBe true
        }

      "the user details connector comes back with an error" in {
        testFailure(
          inSequence{
            mockUserDetails(userDetailsURI)(None)
          },
          _.isInstanceOf[UserInfoServiceError.UserDetailsError] shouldBe true
        )
      }

      "the citizen details connector comes back with an error" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse))
          mockCitizenDetails(nino)(None)
        },
          _.isInstanceOf[UserInfoServiceError.CitizenDetailsError] shouldBe true
        )
      }

      "the user details and citizen details are both missing a surname" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(lastName = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse.copy(person = Some(CitizenDetails.person.copy(lastName = None)))))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo shouldBe Set(MissingUserInfo.Surname)
        )
      }

      "the user details and citizen details are both missing a date of birth" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(dateOfBirth = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse.copy(person = Some(CitizenDetails.person.copy(dateOfBirth = None)))))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo shouldBe Set(MissingUserInfo.DateOfBirth))
      }

      "the user details service doesn't return an email" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(email = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo shouldBe Set(MissingUserInfo.Email))
      }

      "the citizen details service doesn't return an address" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse.copy(address = None)))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo shouldBe Set(MissingUserInfo.Contact))
      }

      "the citizen details service doesn't return a person and user details is missing a surname" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(lastName = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse.copy(person = None)))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo should contain(MissingUserInfo.Surname)
        )
      }

      "the citizen details service doesn't return a person and user details is missing a date of birth" in {
        testFailure(inSequence{
          mockUserDetails(userDetailsURI)(Some(userDetailsResponse.copy(dateOfBirth = None)))
          mockCitizenDetails(nino)(Some(citizenDetailsResponse.copy(person = None)))
        },
          _.asInstanceOf[UserInfoServiceError.MissingUserInfos].missingInfo should contain(MissingUserInfo.DateOfBirth)
        )
      }

    }

  }
}
