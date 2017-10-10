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

import play.api.Configuration
import uk.gov.hmrc.helptosave.repo.UserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.services.UserCapService.dateFormat
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// scalastyle:off magic.number
class UserCapServiceSpec extends TestSupport with ServicesConfig {

  val userCapStore = mock[UserCapStore]

  def result[T](awaitable: Future[T]): T = Await.result(awaitable, 5.seconds)

  "The UserCapService" when {

    val today = LocalDate.now

    val formattedToday = dateFormat.format(today)
    val formattedYesterday = dateFormat.format(today.minusDays(1))

      def mockUserCapStoreGetOne(userCap: Option[UserCap]) =
        (userCapStore.getOne: () ⇒ Future[Option[UserCap]]).expects()
          .returning(Future.successful(userCap))

      def mockUserCapStoreGetOneFailure() =
        (userCapStore.getOne: () ⇒ Future[Option[UserCap]]).expects()
          .returning(Future.failed(new RuntimeException("oh no")))

      def mockUserCapStoreUpsert(userCap: UserCap) =
        (userCapStore.upsert(_: UserCap)).expects(userCap)
          .returning(Future.successful(Some(userCap)))

    "checking if account create is allowed" should {

      "return false if dailyCap is set to 0" in {

        val configOverride = fakeApplication.configuration.++(Configuration("microservice.user-cap.daily.limit" -> 0))
        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        result(userCapService.isAccountCreateAllowed) shouldBe false

      }

      "return false if totalCap is set to 0" in {

        val configOverride = fakeApplication.configuration.++(Configuration("microservice.user-cap.total.limit" -> 0))
        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        result(userCapService.isAccountCreateAllowed) shouldBe false

      }

      "both dailyCap and totalCap are enabled" must {

        val userCapService = new UserCapServiceImpl(userCapStore, fakeApplication.configuration)

        "allow account creation if both dailyCap and totalCap are not reached" in {

          val userCap = UserCap(formattedToday, 9, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe true
        }

        "not allow account creation if dailyCap is reached " in {

          val userCap = UserCap(formattedToday, 11, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe false
        }

        "not allow account creation if totalCap is reached " in {

          val userCap = UserCap(formattedToday, 9, 10000)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe false
        }

        "allow account creation if dailyCap was reached yesterday but not today and totalCap not reached " in {

          val userCap = UserCap(formattedYesterday, 11, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe true
        }
      }

      "when dailyCap is disabled and totalCap is enabled" must {

        val configOverride = fakeApplication.configuration.++(Configuration("microservice.user-cap.daily.enabled" -> false))

        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        "allow account creation as long as totalCap is not reached" in {
          val userCap = UserCap(formattedYesterday, 113, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe true
        }
      }

      "when dailyCap is enabled and totalCap is disabled" must {

        val configOverride = fakeApplication.configuration.++(Configuration("microservice.user-cap.total.enabled" -> false))

        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        "allow account creation as long as dailyCap is not reached" in {
          val userCap = UserCap(formattedYesterday, 9, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe true
        }

        "not allow account creation if the dailyCap is already reached" in {
          val userCap = UserCap(formattedToday, 10, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe false
        }
      }

      "when both dailyCap and totalCap are disabled" must {

        val configOverride = fakeApplication.configuration.++(Configuration("microservice.user-cap.daily.enabled" -> false,
          "microservice.user-cap.total.enabled" -> false))

        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        "always allow account creation" in {
          val userCap = UserCap(formattedYesterday, 9, 100)
          mockUserCapStoreGetOne(Some(userCap))

          result(userCapService.isAccountCreateAllowed) shouldBe true
        }
      }

      "handle the exceptions and return true" in {
        mockUserCapStoreGetOneFailure()
        val userCapService = new UserCapServiceImpl(userCapStore, fakeApplication.configuration)

        result(userCapService.isAccountCreateAllowed) shouldBe true
      }
    }

    "updating the user-cap" when {

      "any one of dailyCap or totalCap is enabled" should {

        val userCapService = new UserCapServiceImpl(userCapStore, fakeApplication.configuration)

        "successfully update the daily count and total counts if there are no existing records" in {
          mockUserCapStoreGetOne(None)
          mockUserCapStoreUpsert(UserCap(formattedToday, 1, 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "successfully update the daily count and total counts if there is no record for today but yesterday" in {
          val userCap = UserCap(formattedYesterday, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(formattedToday, 1, userCap.totalCount + 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "successfully update the daily count and total counts if there is a record for today" in {
          val userCap = UserCap(formattedToday, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(formattedToday, userCap.dailyCount + 1, userCap.totalCount + 1))

          result(userCapService.update()) shouldBe ((): Unit)
        }

      }

      "both dailyCap and totalCap are disabled" should {

        val configOverride = fakeApplication.configuration.++(
          Configuration("microservice.user-cap.daily.enabled" -> false, "microservice.user-cap.total.enabled" -> false))

        val userCapService = new UserCapServiceImpl(userCapStore, configOverride)

        "update both the counts as 0 and delete previous record if any" in {
          val userCap = UserCap(formattedYesterday, 1, 10)
          mockUserCapStoreGetOne(Some(userCap))
          mockUserCapStoreUpsert(UserCap(formattedToday, 0, 0))

          result(userCapService.update()) shouldBe ((): Unit)
        }

        "update both the counts as 0 and delete previous record if any xxxx" in {
          mockUserCapStoreGetOne(None)
          mockUserCapStoreUpsert(UserCap(formattedToday, 0, 0))

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
