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

package uk.gov.hmrc.helptosave.services

import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import play.api.Configuration
import uk.gov.hmrc.helptosave.models.UserCapResponse
import uk.gov.hmrc.helptosave.repo.UserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

// scalastyle:off magic.number
class UserCapServiceSpec extends TestSupport {

  private val userCapStore = mock[UserCapStore]

  def result[T](awaitable: Future[T]): T = Await.result(awaitable, 5.seconds)

  def newUserCapService(config: String) = {
    val servicesConfig: ServicesConfig = buildFakeApplication(Configuration(ConfigFactory.parseString(config))).injector
      .instanceOf[ServicesConfig]
    new UserCapServiceImpl(userCapStore, servicesConfig)
  }

  "The UserCapService" when {

    val today = LocalDate.now

    val yesterday = today.minusDays(1)

    lazy val dailyLimit = servicesConfig.getInt("microservice.user-cap.daily.limit")

    lazy val totalLimit = servicesConfig.getInt("microservice.user-cap.total.limit")

    def mockUserCapStoreGetOne(userCap: Option[UserCap]) =
      when(userCapStore
        .get())
        .thenReturn(Future.successful(userCap))

    def mockUserCapStoreGetOneFailure() =
      when(userCapStore
        .get())
        .thenReturn(Future.failed(new RuntimeException("oh no")))

    def mockUserCapStoreUpsert(userCap: UserCap) =
      when(userCapStore
        .upsert(userCap))
        .thenReturn(Future.successful(Some(userCap)))

    "checking if account create is allowed" must {

      "return response with isDailyCapDisabled = true and isTotalCapDisabled = true if both caps are set to 0" in {

        val userCapService = newUserCapService("""
                                                 | microservice.user-cap.daily.limit = 0
                                                 | microservice.user-cap.total.limit = 0
          """.stripMargin)

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(
          isDailyCapDisabled = true,
          isTotalCapDisabled = true)

      }

      "show daily cap has been reached page if daily-cap is set to 0 but total-cap is not 0" in {

        val userCapService = newUserCapService("""
                                                 | microservice.user-cap.daily.limit = 0
          """.stripMargin)

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isDailyCapDisabled = true)

      }

      "show total cap has been reached page if total-cap is set to 0 but daily-cap is not 0" in {

        val userCapService = newUserCapService("""
                                                 | microservice.user-cap.total.limit = 0
          """.stripMargin)

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isTotalCapDisabled = true)

      }

      "allow account creation incase of any exceptions" in {
        mockUserCapStoreGetOneFailure()
        val userCapService = newUserCapService("")

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }
    }

    "checking if account create is allowed when both dailyCap and totalCap are enabled" must {

      val userCapService = newUserCapService("")

      "allow account creation if both dailyCap and totalCap are not reached" in {

        val userCap = UserCap(today, dailyLimit - 1, totalLimit - 1)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }

      "not allow account creation if dailyCap is reached " in {

        val userCap = UserCap(today, dailyLimit, totalLimit - 1)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isDailyCapReached = true)
      }

      "not allow account creation if totalCap is reached yesterday " in {

        val userCap = UserCap(yesterday, 0, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isTotalCapReached = true)
      }

      "not allow account creation if totalCap is reached today " in {

        val userCap = UserCap(today, dailyLimit - 1, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isTotalCapReached = true)
      }

      "allow account creation if dailyCap was reached yesterday but not today and totalCap not reached " in {

        val userCap = UserCap(yesterday, dailyLimit, totalLimit - 1)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }

      "allow account creation if not mongo record exists for the user cap" in {

        mockUserCapStoreGetOne(None)

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }
    }

    "checking if account create is allowed when dailyCap is disabled and totalCap is enabled" must {

      val userCapService = newUserCapService("""
                                               | microservice.user-cap.daily.enabled = false
        """.stripMargin)

      "allow account creation as long as totalCap is not reached" in {
        val userCap = UserCap(yesterday, dailyLimit, totalLimit - 1)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }

      "not allow account creation if totalCap is reached " in {

        val userCap = UserCap(today, dailyLimit, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isTotalCapReached = true)
      }
    }

    "checking if account create is allowed when dailyCap is enabled and totalCap is disabled" must {

      val userCapService = newUserCapService("""
                                               | microservice.user-cap.total.enabled = false
        """.stripMargin)

      "allow account creation as long as dailyCap is not reached with no record today " in {
        val userCap = UserCap(yesterday, dailyLimit, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }

      "allow account creation if the dailyCap is not reached with record today " in {
        val userCap = UserCap(today, dailyLimit - 1, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }

      "not allow account creation if the dailyCap is already reached" in {
        val userCap = UserCap(today, dailyLimit, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse(isDailyCapReached = true)
      }
    }

    "checking if account create is allowed when both dailyCap and totalCap are disabled" must {

      val userCapService = newUserCapService("""
                                               | microservice.user-cap.daily.enabled = false
                                               | microservice.user-cap.total.enabled = false
        """.stripMargin)

      "always allow account creation" in {
        val userCap = UserCap(today, dailyLimit, totalLimit)
        mockUserCapStoreGetOne(Some(userCap))

        result(userCapService.isAccountCreateAllowed()) shouldBe UserCapResponse()
      }
    }

    "updating the user-cap" when {

      "any one of dailyCap or totalCap is enabled" must {

        val userCapService = newUserCapService("")

        "successfully update the daily count and total counts if there are no existing records" in {
          mockUserCapStoreGetOne(None)
          mockUserCapStoreUpsert(UserCap(today, 1, 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "successfully update the daily count and total counts if there is no record for today but yesterday" in {
          val userCap = UserCap(yesterday, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(today, 1, userCap.totalCount + 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "successfully update the daily count and total counts if there is a record for today" in {
          val userCap = UserCap(today, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(today, userCap.dailyCount + 1, userCap.totalCount + 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

      }

      "both dailyCap and totalCap are disabled" must {

        val userCapService = newUserCapService("""
                                                 | microservice.user-cap.daily.enabled = false
                                                 | microservice.user-cap.total.enabled = false
          """.stripMargin)

        // true true
        "update both the counts as 0 - given there is an existing record" in {
          val userCap = UserCap(yesterday, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(today, 0, 0))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        // false false
        "update both the counts as 0 - given there is no existing record" in {
          mockUserCapStoreGetOne(None)
          mockUserCapStoreUpsert(UserCap(today, 0, 0))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "handle errors successfully and return a success response" in {
          mockUserCapStoreGetOneFailure()
          result(userCapService.update()) shouldBe ((): Unit)
        }
      }
    }
  }
}
