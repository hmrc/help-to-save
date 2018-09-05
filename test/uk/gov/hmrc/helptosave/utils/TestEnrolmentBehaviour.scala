/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.controllers.EnrolmentBehaviour
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

  def mockEnrolmentStoreUpdate(nino: NINO, itmpFlag: Boolean)(result: Either[String, Unit]): Unit =
    (enrolmentStore.update(_: NINO, _: Boolean)(_: HeaderCarrier))
      .expects(nino, itmpFlag, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreInsert(nino: NINO, itmpFlag: Boolean, eligibilityReason: Option[Int], source: String)(result: Either[String, Unit]): Unit =
    (enrolmentStore.insert(_: NINO, _: Boolean, _: Option[Int], _: String)(_: HeaderCarrier))
      .expects(nino, itmpFlag, eligibilityReason, source, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGet(nino: NINO)(result: Either[String, EnrolmentStore.Status]): Unit =
    (enrolmentStore.get(_: NINO)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSetFlag(nino: NINO)(result: Either[String, Unit]): Unit =
    (helpToSaveService.setFlag(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def payloadJson(dobValue: String) =
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
              "communicationPreference" : "preference"
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

  def createAccountJson(dobValue: String): String =
    s"""{
           "payload":${payloadJson(dobValue)},
           "eligibilityReason":7,
           "source": "Digital"
          }""".stripMargin

  val validUserInfoPayload = Json.parse(payloadJson("20200101"))

  val validCreateAccountRequestPayload = Json.parse(createAccountJson("20200101"))
  val validCreateAccountRequest = validCreateAccountRequestPayload.validate[CreateAccountRequest].getOrElse(sys.error("Could not parse CreateAccountRequest"))
  val validNSIUserInfo = validCreateAccountRequest.payload
}
