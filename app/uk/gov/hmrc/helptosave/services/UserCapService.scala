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

package uk.gov.hmrc.helptosave.services

import javax.inject.Singleton

import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import uk.gov.hmrc.helptosave.models.UserCapResponse
import uk.gov.hmrc.helptosave.repo.UserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[UserCapServiceImpl])
trait UserCapService {

  def isAccountCreateAllowed(): Future[UserCapResponse]

  def update(): Future[Unit]

}

@Singleton
class UserCapServiceImpl @Inject() (userCapStore: UserCapStore, configuration: Configuration) extends UserCapService with Logging {

  private val isDailyCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.daily.enabled")

  private val isTotalCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.total.enabled")

  private val dailyCap = configuration.underlying.getInt("microservice.user-cap.daily.limit")

  private val totalCap = configuration.underlying.getInt("microservice.user-cap.total.limit")

  require(dailyCap >= 0 && totalCap >= 0)

  private val check: UserCap ⇒ UserCapResponse = {
    (isTotalCapEnabled, isDailyCapEnabled) match {
      case (false, false) ⇒ _ ⇒
        //This is the normal code path post private-beta, eg: uncapped
        UserCapResponse()

      case (true, true) ⇒ userCap ⇒
        if (userCap.isTodaysRecord) {
          if (userCap.totalCount >= totalCap) {
            UserCapResponse(isTotalCapReached = true)
          } else if (userCap.dailyCount >= dailyCap) {
            UserCapResponse(isDailyCapReached = true)
          } else {
            UserCapResponse()
          }
        } else {
          if (userCap.totalCount >= totalCap) {
            UserCapResponse(isTotalCapReached = true)
          } else {
            UserCapResponse()
          }
        }

      case (true, false) ⇒ userCap ⇒
        if (userCap.totalCount >= totalCap) {
          UserCapResponse(isTotalCapReached = true)
        } else {
          UserCapResponse()
        }

      case (false, true) ⇒ userCap ⇒
        if (userCap.isTodaysRecord && userCap.dailyCount >= dailyCap) {
          UserCapResponse(isDailyCapReached = true)
        } else {
          UserCapResponse()
        }
    }
  }

  override def isAccountCreateAllowed(): Future[UserCapResponse] = {
    if (totalCap === 0 && dailyCap === 0) {
      toFuture(UserCapResponse(isDailyCapDisabled = true, isTotalCapDisabled = true))
    } else if (totalCap === 0) {
      toFuture(UserCapResponse(isTotalCapDisabled = true))
    } else if (dailyCap === 0) {
      toFuture(UserCapResponse(isDailyCapDisabled = true))
    } else {
      userCapStore.get().map(_.fold(UserCapResponse())(check))
        .recover {
          case NonFatal(e) ⇒
            logger.warn("error checking account cap", e)
            UserCapResponse()
        }
    }
  }

  private val calculateUserCap: Option[UserCap] ⇒ UserCap = {
    (isTotalCapEnabled, isDailyCapEnabled) match {
      case (false, false) ⇒ _ ⇒ UserCap(0, 0)
      case (_, _) ⇒ {
        case Some(uc) ⇒
          val c = if (uc.isPreviousRecord) 1 else uc.dailyCount + 1
          UserCap(c, uc.totalCount + 1)
        case None ⇒ UserCap(1, 1)
      }
    }
  }

  override def update(): Future[Unit] =
    userCapStore.get().flatMap {
      userCap ⇒
        userCapStore.upsert(calculateUserCap(userCap)).map{ updatedUserCap ⇒
          val logMessage = "Updated user cap - " + updatedUserCap.fold("could not retrieve user cap data"){ cap ⇒
            s"counts are now (daily: ${cap.dailyCount}, total: ${cap.totalCount})"
          }
          logger.info(logMessage)
        }
    }.recover {
      case e ⇒ logger.warn("error updating the account cap", e)
    }
}

