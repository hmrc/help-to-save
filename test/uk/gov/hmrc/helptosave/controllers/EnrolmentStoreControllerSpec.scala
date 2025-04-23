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

package uk.gov.hmrc.helptosave.controllers

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authProviderId, nino => v2Nino}
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, PAClientId}
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth.*
import uk.gov.hmrc.helptosave.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosave.models.enrolment.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosave.utils.TestEnrolmentBehaviour

import scala.concurrent.Future

class EnrolmentStoreControllerSpec
    extends StrideAuthSupport
    with ScalaCheckDrivenPropertyChecks
    with TestEnrolmentBehaviour {

  implicit val arbEnrolmentStatus: Arbitrary[Status] =
    Arbitrary(
      Gen.oneOf[Status](
        Gen.const(NotEnrolled),
        Gen.oneOf(true, false).map(Enrolled.apply)
      )
    )

  val privilegedCredentials: PAClientId = PAClientId("id")
  val ggCredentials: GGCredId           = GGCredId("123-gg")

  def mockGetAccountFromNSI(nino: String, systemId: String, correlationId: String, path: String)(
    result: Either[String, Option[Account]]
  ): Unit =
    when(proxyConnector.getAccount(eqTo(nino), eqTo(systemId), eqTo(correlationId), eqTo(path))(any(), any()))
      .thenReturn(EitherT.fromEither[Future](result))

  def mockSetAccountNumber(nino: String, accountNumber: String)(result: Either[String, Unit]): Unit =
    when(enrolmentStore.updateWithAccountNumber(eqTo(nino), eqTo(accountNumber))(any()))
      .thenReturn(EitherT.fromEither[Future](result))

  "The EnrolmentStoreController" when {

    val controller =
      new EnrolmentStoreController(enrolmentStore, helpToSaveService, mockAuthConnector, proxyConnector, testCC)
    val nino       = "AE123456C"

    "setting the ITMP flag" must {

      def setFlag(): Future[Result] =
        controller.setITMPFlag()(FakeRequest())

      "set the ITMP flag" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockSetFlag(nino)(Left(""))

        await(setFlag())
      }

      "update the mongo record with the ITMP flag set to true" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockSetFlag(nino)(Right(()))
        mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))

        await(setFlag())
      }

      "return a 200 if all the steps were successful" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockSetFlag(nino)(Right(()))
        mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))

        status(setFlag()) shouldBe OK
      }

      "return a Left if any of the steps failed" in {
        def test(mockActions: => Unit): Unit = {
          mockActions
          status(setFlag()) shouldBe INTERNAL_SERVER_ERROR
        }

        test {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Left(""))
        }

        test {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }
      }

    }

    "getting the user's account number" must {

      def getAccountNumber: Future[Result] =
        controller.getAccountNumber()(FakeRequest())

      val accountNumber = AccountNumber(Some("1234567890123"))
      val correlationId = "-"
      val systemId      = "help-to-save"

      "get the account number from the enrolment store" in {
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEnrolmentStoreGetAccountNumber(nino)(Right(accountNumber))

        val result = await(getAccountNumber)
        contentAsJson(Future.successful(result)) shouldBe Json.toJson(accountNumber)
      }

      "call NSI when the account number cannot be obtained from the enrolment store" in {
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEnrolmentStoreGetAccountNumber(nino)(Right(AccountNumber(None)))
        mockGetAccountFromNSI(nino, systemId, correlationId, "/")(Right(Some(account)))
        mockSetAccountNumber(nino, "AC01")(Right(()))

        val jsonResult: JsValue = Json.parse("""{"accountNumber":"AC01"}""")

        val result = await(getAccountNumber)
        status(result)                           shouldBe OK
        contentAsJson(Future.successful(result)) shouldBe jsonResult
      }

      "return an InternalServerError when call to get account from NSI fails" in {
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEnrolmentStoreGetAccountNumber(nino)(Right(AccountNumber(None)))
        mockGetAccountFromNSI(nino, systemId, correlationId, "/")(Left("An error occurred"))

        val result = await(getAccountNumber)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

    "getting the user enrolment status" must {

      def getEnrolmentStatus(nino: Option[String]): Future[Result] =
        controller.getEnrolmentStatus(nino)(FakeRequest())

      "get the enrolment status from the enrolment store" in {
        mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEnrolmentStoreGet(nino)(Left(""))

        await(getEnrolmentStatus(Some(nino)))
      }

      "return the enrolment status if the call was successful" in {
        val m: Map[Status, String] = Map(
          Enrolled(itmpHtSFlag = true)  ->
            """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : true
              |}
            """.stripMargin,
          Enrolled(itmpHtSFlag = false) ->
            """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin,
          NotEnrolled                   ->
            """
              |{
              |  "enrolled"    : false,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin
        )

        m.foreach { case (s, j) =>
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
          mockAuth(v2Nino)(Right(mockedNinoRetrieval))
          mockEnrolmentStoreGet(nino)(Right(s))

          val result = getEnrolmentStatus(Some(nino))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.parse(j)
        }
      }

      "return an error if the call was not successful" in {
        mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
        mockAuth(v2Nino)(Right(mockedNinoRetrieval))
        mockEnrolmentStoreGet(nino)(Left(""))

        status(getEnrolmentStatus(Some(nino))) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "handling requests to get enrolment status with privileged access" must {

      "ask the enrolment store for the enrolment status and return the result" in {
        List[Status](
          Enrolled(itmpHtSFlag = true),
          Enrolled(itmpHtSFlag = false),
          NotEnrolled
        ).foreach { status =>
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
          mockEnrolmentStoreGet(nino)(Right(status))

          val result = controller.getEnrolmentStatus(Some(nino))(FakeRequest())
          contentAsJson(result).validate[Status] shouldBe JsSuccess(status)
        }
      }

      "return an error if there is a problem getting the enrolment status" in {
        mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
        mockEnrolmentStoreGet(nino)(Left(""))

        val result = controller.getEnrolmentStatus(Some(nino))(FakeRequest())
        status(result) shouldBe 500
      }

    }
  }

}
