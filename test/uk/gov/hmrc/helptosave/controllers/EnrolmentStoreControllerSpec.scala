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

import cats.data.EitherT
import cats.instances.future._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnector
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreControllerSpec extends AuthSupport with GeneratorDrivenPropertyChecks {

  val enrolmentStore: EnrolmentStore = mock[EnrolmentStore]
  val itmpConnector: ITMPEnrolmentConnector = mock[ITMPEnrolmentConnector]

  def mockEnrolmentStoreUpdate(nino: NINO, itmpFlag: Boolean)(result: Either[String, Unit]): Unit =
    (enrolmentStore.update(_: NINO, _: Boolean))
      .expects(nino, itmpFlag)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGet(nino: NINO)(result: Either[String, EnrolmentStore.Status]): Unit =
    (enrolmentStore.get(_: NINO))
      .expects(nino)
      .returning(EitherT.fromEither[Future](result))

  def mockITMPConnector(nino: NINO)(result: Either[String, Unit]): Unit =
    (itmpConnector.setFlag(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit val arbEnrolmentStatus: Arbitrary[EnrolmentStore.Status] =
    Arbitrary(Gen.oneOf[EnrolmentStore.Status](
      Gen.const(EnrolmentStore.NotEnrolled),
      Gen.oneOf(true, false).map(EnrolmentStore.Enrolled)
    ))

  "The EnrolmentStoreController" when {

    val controller = new EnrolmentStoreController(enrolmentStore, itmpConnector, mockAuthConnector)
    val nino = "AE123456C"
    val email = "user@test.com"

    "enrolling a user" must {

        def enrol(): Future[Result] = controller.enrol(nino)(FakeRequest())

      "create a mongo record with the ITMP flag set to false" in {
        mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
        mockEnrolmentStoreUpdate(nino, itmpFlag = false)(Left(""))

        await(enrol())
      }

      "set the ITMP flag" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockEnrolmentStoreUpdate(nino, itmpFlag = false)(Right(()))
          mockITMPConnector(nino)(Left(""))
        }

        await(enrol())
      }

      "update the mongo record with the ITMP flag set to true" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockEnrolmentStoreUpdate(nino, itmpFlag = false)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        await(enrol())
      }

      "return an OK if all the steps were successful" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockEnrolmentStoreUpdate(nino, itmpFlag = false)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        status(enrol()) shouldBe OK
      }

      "return a 500 if any of the steps failed" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockEnrolmentStoreUpdate(nino, itmpFlag = false)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }

        status(enrol()) shouldBe INTERNAL_SERVER_ERROR
      }

    }

    "setting the ITMP flag" must {

        def setFlag(): Future[Result] =
          controller.setITMPFlag(nino)(FakeRequest())

      "set the ITMP flag" in {
        mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
        mockITMPConnector(nino)(Left(""))

        await(setFlag())
      }

      "update the mongo record with the ITMP flag set to true" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }

        await(setFlag())
      }

      "return a 200 if all the steps were successful" in {
        inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockITMPConnector(nino)(Right(()))
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
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockITMPConnector(nino)(Left(""))
        })

        test(inSequence {
          mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        })
      }

    }

    "getting the user enrolment status" must {

        def getEnrolmentStatus(): Future[Result] =
          controller.getEnrolmentStatus(nino)(FakeRequest())

      "get the enrolment status form the enrolment store" in {
        mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
        mockEnrolmentStoreGet(nino)(Left(""))

        await(getEnrolmentStatus())
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
            mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
            mockEnrolmentStoreGet(nino)(Right(s))

            val result = getEnrolmentStatus()
            status(result) shouldBe OK
            contentAsJson(result) shouldBe Json.parse(j)
        }
      }

      "return an error if the call was not successful" in {
        mockAuthResultWithSuccess(AuthWithCL200)(Enrolments(enrolments))
        mockEnrolmentStoreGet(nino)(Left(""))

        status(getEnrolmentStatus()) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

}
