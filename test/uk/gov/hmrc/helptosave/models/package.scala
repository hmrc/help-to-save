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

package uk.gov.hmrc.helptosave

import java.time.LocalDate

import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.helptosave.models.UserInfo
import uk.gov.hmrc.helptosave.models.userinfoapi.{APIUserInfo ⇒ APIUSerInfo}

import scala.reflect.ClassTag
import scala.reflect._

package object models {

  implicit val addressArb: Arbitrary[Address] =
    Arbitrary(for {
      lines ← Gen.listOf(Gen.alphaNumStr)
      postcode ← Gen.alphaNumStr
      country ← Gen.alphaStr
    } yield Address(lines, Some(postcode), Some(country)))

  implicit val userInfoArb: Arbitrary[UserInfo] =
    Arbitrary(for {
      name ← Gen.alphaStr
      surname ← Gen.alphaStr
      nino ← Gen.alphaNumStr
      dob ← Gen.choose(0L, 100L).map(LocalDate.ofEpochDay)
      email ← Gen.alphaNumStr
      address ← addressArb.arbitrary
    } yield UserInfo(name, surname, nino, dob, email, address))


  implicit val enrolmentIdentifierArb: Arbitrary[APIUSerInfo.EnrolmentIdentifier] =
    Arbitrary(for {
      key ← Gen.identifier
      value ← Gen.identifier
    } yield APIUSerInfo.EnrolmentIdentifier(key, value))


  implicit val enrolmentArb: Arbitrary[APIUSerInfo.Enrolment] =
    Arbitrary(for {
      key ← Gen.identifier
      ids ← Gen.listOf(enrolmentIdentifierArb.arbitrary)
      state ← Gen.alphaNumStr
    } yield APIUSerInfo.Enrolment(key, ids, state))

  implicit val apiUserInfoArb: Arbitrary[APIUSerInfo] =
    Arbitrary(for {
      name ← Gen.option(Gen.identifier)
      middleName ← Gen.option(Gen.identifier)
      surname ← Gen.option(Gen.identifier)
      address ← Gen.option(addressArb.arbitrary)
      dob ← Gen.option(Gen.choose(0L, 100L).map(LocalDate.ofEpochDay))
      nino ← Gen.option(Gen.identifier)
      enrolments ← Gen.option(Gen.listOf(enrolmentArb.arbitrary))
      email ← Gen.option(Gen.identifier).map(_.map(_ + "@example.com"))
    } yield APIUSerInfo(name, surname, middleName,
      address.map( a ⇒ APIUSerInfo.Address(a.lines.mkString("\n"), a.postcode, a.country)),
      dob, nino, enrolments, email)
    )


  def sample[A: ClassTag](a: Arbitrary[A]): A = a.arbitrary.sample.getOrElse(
    sys.error(s"Could not generate ${classTag[A].getClass.getSimpleName}"))

  def randomAPIUserInfo(): APIUSerInfo = sample(apiUserInfoArb)

  def randomUserInfo(): UserInfo = sample(userInfoArb)
}
