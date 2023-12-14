/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.utils

import java.time.{LocalDate, YearMonth}

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.connectors.{DESConnector, HelpToSaveProxyConnector}
import uk.gov.hmrc.helptosave.controllers.EnrolmentBehaviour
import uk.gov.hmrc.helptosave.models.account.{Account, AccountNumber, Blocking, BonusTerm}
import uk.gov.hmrc.helptosave.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.services.HelpToSaveService
import uk.gov.hmrc.helptosave.util._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait TestEnrolmentBehaviour extends TestSupport {

  val enrolmentStore: EnrolmentStore = mock[EnrolmentStore]
  val itmpConnector: DESConnector = mock[DESConnector]
  val enrolmentBehaviour: EnrolmentBehaviour = mock[EnrolmentBehaviour]
  val helpToSaveService: HelpToSaveService = mock[HelpToSaveService]
  val proxyConnector: HelpToSaveProxyConnector = mock[HelpToSaveProxyConnector]

  def mockEnrolmentStoreUpdate(nino: NINO, itmpFlag: Boolean)(result: Either[String, Unit]): Unit =
    (enrolmentStore.updateItmpFlag(_: NINO, _: Boolean)(_: HeaderCarrier))
      .expects(nino, itmpFlag, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreInsert(nino: NINO, itmpFlag: Boolean, eligibilityReason: Option[Int],
                               source: String, accountNumber: Option[String], deleteFlag: Option[Boolean] = None)(result: Either[String, Unit]): Unit =
    (enrolmentStore.insert(_: NINO, _: Boolean, _: Option[Int], _: String, _: Option[String], _: Option[Boolean])(_: HeaderCarrier))
      .expects(nino, itmpFlag, eligibilityReason, source, accountNumber, deleteFlag, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGet(nino: NINO)(result: Either[String, EnrolmentStore.Status]): Unit =
    (enrolmentStore.get(_: NINO)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGetAccountNumber(nino: NINO)(result: Either[String, AccountNumber]): Unit =
    (enrolmentStore.getAccountNumber(_: NINO)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSetFlag(nino: NINO)(result: Either[String, Unit]): Unit =
    (helpToSaveService.setFlag(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def payloadJson(dobValue: String, communicationPreference: String = "02"): String =
    s"""{
            "nino" : "nino",
            "forename" : "name",
            "surname" : "surname",
            "dateOfBirth" : $dobValue,
            "contactDetails" : {
              "address1" : "1",
              "address2" : "2",
              "postcode": "postcode",
              "countryCode" : "country",
              "communicationPreference" : "$communicationPreference"
            },
            "nbaDetails": {
               "sortCode" : "20-12-12",
               "accountNumber" : "12345678",
               "rollNumber" : "11",
               "accountName" : "test"
             },
            "registrationChannel" : "online",
            "version" : "V2.0",
            "systemId" : "MDTP REGISTRATION"
      }""".stripMargin

  def createAccountJson(dobValue: String, detailsManuallyEntered: Boolean, communicationPreference: String = "02"): String =
    s"""{
           "payload":${payloadJson(dobValue, communicationPreference)},
           "eligibilityReason":7,
           "source": "Digital",
           "detailsManuallyEntered" : $detailsManuallyEntered
          }""".stripMargin

  val validUserInfoPayload = Json.parse(payloadJson("20200101"))

  def validCreateAccountRequestPayload(detailsManuallyEntered:  Boolean = false,
                                       communicationPreference: String  = "02") =
    Json.parse(createAccountJson("20200101", detailsManuallyEntered, communicationPreference))

  val validCreateAccountRequest = validCreateAccountRequestPayload()
    .validate[CreateAccountRequest](CreateAccountRequest.createAccountRequestReads(Some(appConfig.createAccountVersion)))
    .getOrElse(sys.error("Could not parse CreateAccountRequest"))

  val validUpdateAccountRequest = validCreateAccountRequest.copy(payload = validCreateAccountRequest.payload.copy(systemId = None, version = None))

  val validNSIUserInfo = validCreateAccountRequest.payload

  val account = Account(
    YearMonth.of(2018, 1),
    "AC01", false,
    Blocking(false, false, false),
    200.34,
    34.50,
    15.50,
    50.00,
    LocalDate.parse("2018-02-28"),
    "Testforename",
    "Testsurname",
    Some("test@example.com"),
    List(
      BonusTerm(bonusEstimate          = 123.45, bonusPaid = 123.45, startDate = LocalDate.parse("2018-01-01"), endDate = LocalDate.parse("2019-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2020-01-01")),
      BonusTerm(bonusEstimate          = 67.00, bonusPaid = 0.00, startDate = LocalDate.parse("2020-01-01"), endDate = LocalDate.parse("2021-12-31"), bonusPaidOnOrAfterDate = LocalDate.parse("2022-01-01"))
    ),
    None,
    None)
}
