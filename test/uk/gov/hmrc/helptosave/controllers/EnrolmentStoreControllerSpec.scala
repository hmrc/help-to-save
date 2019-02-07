/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsSuccess, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, PAClientId}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authProviderId, nino ⇒ v2Nino}
import uk.gov.hmrc.helptosave.controllers.HelpToSaveAuth._
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.utils.TestEnrolmentBehaviour

import scala.concurrent.Future

class EnrolmentStoreControllerSpec extends StrideAuthSupport with GeneratorDrivenPropertyChecks with TestEnrolmentBehaviour {

  implicit val arbEnrolmentStatus: Arbitrary[EnrolmentStore.Status] =
    Arbitrary(Gen.oneOf[EnrolmentStore.Status](
      Gen.const(EnrolmentStore.NotEnrolled),
      Gen.oneOf(true, false).map(EnrolmentStore.Enrolled)
    ))

  val privilegedCredentials = PAClientId("id")
  val ggCredentials = GGCredId("123-gg")

  "The EnrolmentStoreController" when {

    val controller = new EnrolmentStoreController(enrolmentStore, helpToSaveService, mockAuthConnector)
    val nino = "AE123456C"

    "setting the ITMP flag" must {

        def setFlag(): Future[Result] =
          controller.setITMPFlag()(FakeRequest())

      "set the ITMP flag" in {
        mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
        mockSetFlag(nino)(Left(""))

        await(setFlag())
      }

      "update the mongo record with the ITMP flag set to true" in {
        inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }

        await(setFlag())
      }

      "return a 200 if all the steps were successful" in {
        inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        status(setFlag()) shouldBe OK
      }

      "return a Left if any of the steps failed" in {
          def test(mockActions: ⇒ Unit): Unit = {
            mockActions
            status(setFlag()) shouldBe INTERNAL_SERVER_ERROR
          }

        test(inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Left(""))
        })

        test(inSequence {
          mockAuth(AuthWithCL200, v2Nino)(Right(mockedNinoRetrieval))
          mockSetFlag(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        })
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
        val m: Map[EnrolmentStore.Status, String] = Map(
          EnrolmentStore.Enrolled(true) →
            """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : true
              |}
            """.stripMargin,
          EnrolmentStore.Enrolled(false) →
            """
              |{
              |  "enrolled"    : true,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin,
          EnrolmentStore.NotEnrolled →
            """
              |{
              |  "enrolled"    : false,
              |  "itmpHtSFlag" : false
              |}
            """.stripMargin
        )

        m.foreach{
          case (s, j) ⇒
            mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(ggCredentials))
            mockAuth(v2Nino)(Right(mockedNinoRetrieval))
            mockEnrolmentStoreGet(nino)(Right(s))

            val result = getEnrolmentStatus(Some(nino))
            status(result) shouldBe OK
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

    "handling requests to get enrolment status via calls from Stride" must {

      "ask the enrolment store for the enrolment status and return the result" in {
        List[EnrolmentStore.Status](
          EnrolmentStore.Enrolled(true),
          EnrolmentStore.Enrolled(false),
          EnrolmentStore.NotEnrolled
        ).foreach{ status ⇒
            inSequence {
              mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
              mockEnrolmentStoreGet(nino)(Right(status))
            }

            val result = controller.getEnrolmentStatus(Some(nino))(FakeRequest())
            contentAsJson(result).validate[EnrolmentStore.Status] shouldBe JsSuccess(status)
          }
      }

      "return an error if there is a problem getting the enrolment status" in {
        inSequence {
          mockAuth(GGAndPrivilegedProviders, authProviderId)(Right(privilegedCredentials))
          mockEnrolmentStoreGet(nino)(Left(""))
        }

        val result = controller.getEnrolmentStatus(Some(nino))(FakeRequest())
        status(result) shouldBe 500
      }

    }
  }

}
