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

package uk.gov.hmrc.helptosaveeligibilitycheck.models


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.cartesian._
import play.api.libs.json._

import scala.annotation.tailrec
import scala.util.matching.Regex

/**
  * The user information required by NS&I to create an account.
  */
case class NSIUserInfo(forename: String,
                       surname: String,
                       birthdate: LocalDate,
                       address1: String,
                       address2: String,
                       address3: Option[String],
                       address4: Option[String],
                       address5: Option[String],
                       postcode: String,
                       countryCode: Option[String],
                       NINO: String,
                       communicationPreference: String,
                       phoneNumber: Option[String],
                       emailAddress: String,
                       registrationChannel: String)

object NSIUserInfo {

  /**
    * Performs validation checks on the given [[UserInfo]] and converts to [[NSIUserInfo]]
    * if successful.
    */
  def apply(userInfo: UserInfo): ValidatedNel[String, NSIUserInfo] =
    (forenameValidation(userInfo.forename) |@|
      surnameValidation(userInfo.surname) |@|
      dateValidation(userInfo.dateOfBirth) |@|
      addressLineValidation(userInfo.address) |@|
      postcodeValidation(userInfo.address.postcode) |@|
      countryCodeValidation(userInfo.address.country) |@|
      ninoValidation(userInfo.NINO) |@|
      emailValidation(userInfo.email)).map(
      (forename, surname, dateOfBirth, addressLines, postcode, countryCode, nino, email) ⇒
        NSIUserInfo(forename, surname, dateOfBirth, addressLines.line1, addressLines.line2,
          addressLines.line3, addressLines.line4, addressLines.line5, postcode, countryCode,
          nino, communicationPreference = "02", phoneNumber = None, email, registrationChannel = "online")
    )

  implicit val dateWrites: Writes[LocalDate] = new Writes[LocalDate] {
    override def writes(o: LocalDate): JsValue = JsString(o.format(DateTimeFormatter.ofPattern("YYYYMMdd")))
  }

  implicit val nsiUserInfoWrites: Format[NSIUserInfo] = Json.format[NSIUserInfo]

  private case class ValidatedAddressLines(line1: String,
                                           line2: String,
                                           line3: Option[String],
                                           line4: Option[String],
                                           line5: Option[String])

  private case class Email(local: String, domain: String)

  private def forenameValidation(name: String): ValidatedNel[String, String] = {
    val characterCountLowerBoundCheck = validatedFromBoolean(name)(_.nonEmpty, "forename did not have any characters")
    val characterCountUpperBoundCheck = validatedFromBoolean(name)(_.length <= 26, "forename was larger than 26 characters")

    (commonNameChecks(name, "forename") |@| characterCountLowerBoundCheck |@| characterCountUpperBoundCheck)
      .tupled.map(_ ⇒ name)
  }

  private def surnameValidation(name: String): ValidatedNel[String, String] = {
    val characterCountLowerBoundCheck = validatedFromBoolean(name)(_.length > 1, "surname did not have at least 2 characters")
    val characterCountUpperBoundCheck = validatedFromBoolean(name)(_.length <= 300, "surname was larger than 300 characters")

    val consecutiveAlphaCharCheck = validatedFromBoolean(name)(containsNConsecutiveAlphaCharacters(_, 2),
      "surname did not have at least 2 consecutive alphabetic characters")

    (commonNameChecks(name, "surname") |@| characterCountLowerBoundCheck |@|
      characterCountUpperBoundCheck |@| consecutiveAlphaCharCheck)
      .tupled.map(_ ⇒ name)
  }

  private def dateValidation(date: LocalDate): ValidatedNel[String, LocalDate] = {
    val lowerBoundCheck =
      validatedFromBoolean(date)(_.isAfter(LocalDate.of(1800, 1, 1)), s"Birth date $date was before 01/01/1800")
    val upperBoundCheck =
      validatedFromBoolean(date)(_.isBefore(LocalDate.now()), s"Birth date $date was in the future")

    (lowerBoundCheck |@| upperBoundCheck).tupled.map(_ ⇒ date)
  }

  private def addressLineValidation(address: Address): ValidatedNel[String, ValidatedAddressLines] = {
    val list = List(address.line1, address.line2, address.line3,
      address.line4, address.line5).collect { case Some(s) ⇒ s }.filter(_.nonEmpty)

    val lengthCheck = validatedFromBoolean(list)(!_.exists(_.length > 28),
      "Address contained line greater than 28 characters")

    val lineCheck = list match {
      case l1 :: l2 :: l3 :: l4 :: l5 :: Nil ⇒
        Valid(ValidatedAddressLines(l1, l2, Some(l3), Some(l4), Some(l5)))
      case l1 :: l2 :: l3 :: l4 :: Nil ⇒
        Valid(ValidatedAddressLines(l1, l2, Some(l3), Some(l4), None))
      case l1 :: l2 :: l3 :: Nil ⇒
        Valid(ValidatedAddressLines(l1, l2, Some(l3), None, None))
      case l1 :: l2 :: Nil ⇒
        Valid(ValidatedAddressLines(l1, l2, None, None, None))
      case _ ⇒
        Invalid(NonEmptyList.of("Could not find two lines of address"))
    }

    (lengthCheck |@| lineCheck).map((_, lines) ⇒ lines)
  }

  private def postcodeValidation(postcode: Option[String]): ValidatedNel[String, String] = postcode match {
    case None ⇒
      Invalid(NonEmptyList.of("Postcode undefined"))

    case Some(p) ⇒
      val trimmedPostcode = p.replaceAllLiterally(" ", "")
      val lengthCheck =
        validatedFromBoolean(trimmedPostcode)(_.length <= 10, "Postcode was longer thn 10 characters")

      val regexCheck = regexValidation(trimmedPostcode)(postcodeRegex, "Invalid postcode format")

      (lengthCheck |@| regexCheck).tupled.map(_ ⇒ trimmedPostcode)
  }

  private def countryCodeValidation(countryCode: Option[String]): ValidatedNel[String, Option[String]] =
    validatedFromBoolean(countryCode.map(_.trim))(_.forall(_.length == 2), "Country code was not ")

  private def ninoValidation(nino: String): ValidatedNel[String, String] = {
    val lengthCheck =
      validatedFromBoolean(nino)(_.length <= 9, "NINO was longer thn 9 characters")

    val regexCheck =
      regexValidation(nino)(ninoRegex, "Invalid NINO format")

    (lengthCheck |@| regexCheck).tupled.map(_ ⇒ nino)
  }

  private def emailValidation(email: String): ValidatedNel[String, String] = {
    val lengthCheck = validatedFromBoolean(email)(_.length <= 56, "email was longer than 56 characters")
    val whitespaceCheck = validatedFromBoolean(email)(!_.contains(' '), "email contained whitespace")
    val atCheck = validatedFromBoolean(email)(_.contains('@'), "email did not contain '@' symbol")

    // the domain is the part of the email after the last '@' symbol - this is
    // on the assumption that the domain cannot contain an '@' symbol
    val maybeEmail = email.split("@").reverse.toList match {
      case d :: (l@(_ :: _)) ⇒ Some(Email(l.mkString("@"), d))
      case _ ⇒ None
    }

    val localCheck = validatedFromBoolean(maybeEmail)(
      _.forall(!_.local.endsWith(".")), "local part ended with '.'")

    val domainDotCheck1 = validatedFromBoolean(maybeEmail)(
      _.forall(_.domain.contains('.')),
      "domain did not contain '.' character")

    val domainDotCheck2 = validatedFromBoolean(maybeEmail)(
      _.forall(!_.domain.startsWith(".")), "domain started with '.'")

    val domainLastPartCheck = validatedFromBoolean(maybeEmail)(
      _.forall(_.domain.split('.').lastOption.forall(_.length > 2)),
      "last part of domain did not have at least two characters")

    (lengthCheck |@| whitespaceCheck |@| atCheck |@| localCheck |@|
      domainDotCheck1 |@| domainDotCheck2 |@| domainLastPartCheck)
      .tupled.map(_ ⇒ email)
  }

  private def commonNameChecks(name: String, nameType: String): ValidatedNel[String, String] = {
    val allowedSpecialCharacters = List('-', '&')
    val forbiddenSpecialCharacters = specialCharacters(name, allowedSpecialCharacters)

    val leadingSpaceCheck = validatedFromBoolean(name)(!_.startsWith(" "), s"$nameType started with leading space")

    val numericCheck = validatedFromBoolean(name)(!_.exists(_.isDigit), s"$nameType contained numeric characters")

    val specialCharacterCheck = validatedFromBoolean(forbiddenSpecialCharacters)(_.isEmpty,
      s"$nameType contained invalid special characters: ${forbiddenSpecialCharacters.mkString(", ")}")

    val firstCharacterNonSpecial = validatedFromBoolean(name)(!_.headOption.exists(isSpecial(_)),
      s"$nameType started with special character")

    val lastCharacterNonSpecial = validatedFromBoolean(name)(!_.lastOption.exists(isSpecial(_)),
      s"$nameType ended with special character")

    val consecutiveSpecialCharacters = validatedFromBoolean(name)(!containsNConsecutiveSpecialCharacters(_, 2),
      s"$nameType contained consecutive special characters")

    (leadingSpaceCheck |@| numericCheck |@| specialCharacterCheck |@|
      firstCharacterNonSpecial |@| lastCharacterNonSpecial |@| consecutiveSpecialCharacters)
      .tupled.map(_ ⇒ name)
  }

  private def validatedFromBoolean[A](a: A)(isValid: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
    if (isValid(a)) Validated.Valid(a) else Validated.Invalid(NonEmptyList.of(ifFalse))

  private def regexValidation(s: String)(regex: Regex, ifInvalid: ⇒ String): ValidatedNel[String, String] =
    validatedFromBoolean(s)(regex.pattern.matcher(_).matches(), ifInvalid)

  /**
    * The given [[Char]] is special if the following is true:
    * - it is not a whitespace character
    * - it is not alphanumeric
    * - it is not contained in `ignore`
    */
  private def isSpecial(c: Char, ignore: List[Char] = List.empty[Char]): Boolean =
    !(c == ' ' || c.isLetterOrDigit || ignore.contains(c))

  /**
    * Return a list of distinct special characters contained in the given string. Special
    * characters found which are contained in `ignore` are not returned
    */
  private def specialCharacters(s: String, ignore: List[Char] = List.empty[Char]): List[Char] =
    s.replaceAllLiterally(" ", "").filter(isSpecial(_, ignore)).toList.distinct

  /** Does the given string contain `n` or more consecutive special characters? */
  private def containsNConsecutiveSpecialCharacters(s: String, n: Int): Boolean =
    containsNConsecutive(s, n, isSpecial(_))

  private def containsNConsecutiveAlphaCharacters(s: String, n: Int): Boolean =
    containsNConsecutive(s, n, _.isLetter)

  /**
    * Does the given string contains `n` consecutive characters which satisfy the given predicate?
    * `n` should be two or greater.
    */
  private def containsNConsecutive(s: String, n: Int, predicate: Char ⇒ Boolean): Boolean = {
    @tailrec
    def loop(s: List[Char], previous: Char, count: Int): Boolean = s match {
      case Nil ⇒
        false

      case head :: tail ⇒
        if (predicate(head) && predicate(previous)) {
          val newCount = count + 1
          if (newCount + 1 == n) {
            true
          } else {
            loop(tail, head, newCount)
          }
        } else {
          loop(tail, head, 0)
        }
    }

    if (n > 1) {
      s.toList match {
        // only loop over strings that have length two or greater
        case head :: (tail@(_ :: _)) ⇒ loop(tail, head, 0)
        case _ ⇒ false
      }
    } else {
      false
    }
  }

  private[models] val ninoRegex = ("""^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z])([0-9]{2})([0-9]{2})""" +
    """([0-9]{2})([A-D]{1})|((XX)(99)(99)(99)(X)))$""").r

  private[models] val postcodeRegex =
    ("""^((GIR)(0AA)|([A-PR-UWYZ]([0-9]{1,2}|([A-HK-Y][0-9]|[A-HK-Y][0-9]([0-9]|[ABEHMNPRV-Y]))|""" +
      """[0-9][A-HJKS-UW]))([0-9][ABD-HJLNP-UW-Z]{2})|(([A-Z]{1,4})(1ZZ))|((BFPO)([0-9]{1,4})))$""").r

}

