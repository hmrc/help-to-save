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

import javax.inject.Singleton

import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import uk.gov.hmrc.helptosave.repo.UserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[UserCapServiceImpl])
trait UserCapService {

  def isAccountCreateAllowed(): Future[Boolean]

  def update(): Future[Unit]

}

@Singleton
class UserCapServiceImpl @Inject() (userCapStore: UserCapStore, configuration: Configuration) extends UserCapService with Logging {

  private val isDailyCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.daily.enabled")

  private val isTotalCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.total.enabled")

  private val dailyCap = configuration.underlying.getInt("microservice.user-cap.daily.limit")

  private val totalCap = configuration.underlying.getInt("microservice.user-cap.total.limit")

  require(dailyCap >= 0 && totalCap >= 0)

  private val check: UserCap ⇒ Boolean = {
    (isTotalCapEnabled, isDailyCapEnabled) match {
      case (true, true) ⇒ userCap ⇒
        if (userCap.isTodaysRecord) {
          userCap.dailyCount < dailyCap && userCap.totalCount < totalCap
        } else {
          userCap.totalCount < totalCap
        }

      case (true, false) ⇒ userCap ⇒ userCap.totalCount < totalCap

      case (false, true) ⇒ userCap ⇒
        if (userCap.isTodaysRecord) {
          userCap.dailyCount < dailyCap
        } else {
          true
        }
      case (false, false) ⇒ userCap ⇒ true
    }

  }

  //  private val doCheck: Option[UserCap] ⇒ Boolean = {
  //    (dailyCap, totalCap) match {
  //      case (0, _) | (_, 0) ⇒ userCap ⇒ false
  //      case (_, _) ⇒
  //        userCap ⇒ userCap.forall(check)
  //    }
  //  }

  override def isAccountCreateAllowed(): Future[Boolean] = {
    if (dailyCap === 0 || totalCap === 0) {
      toFuture(false)
    } else {
      userCapStore.get().map(_.forall(check))
        .recover {
          case NonFatal(e) ⇒
            logger.warn("error checking account cap", e)
            true
        }
    }
  }

  private val doUpdate: Option[UserCap] ⇒ UserCap = {
    (isTotalCapEnabled, isDailyCapEnabled) match {
      case (false, false) ⇒ userCap ⇒ UserCap(0, 0)
      case (_, _) ⇒ {
        case Some(uc) ⇒
          val c = if (uc.isPreviousRecord) 1 else uc.dailyCount + 1
          UserCap(c, uc.totalCount + 1)
        case None ⇒ UserCap(1, 1)
      }
    }
  }

  override def update(): Future[Unit] =
    userCapStore.get().map(doUpdate)
      .map(newRecord ⇒ userCapStore.upsert(newRecord))
      .map(_ ⇒ ())
      .recover {
        case e ⇒ logger.warn("error updating the account cap", e)
      }
}

