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
import java.time.format.DateTimeFormatter
import javax.inject.Singleton

import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import uk.gov.hmrc.helptosave.repo.UserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap
import uk.gov.hmrc.helptosave.services.UserCapService.dateFormat
import uk.gov.hmrc.helptosave.util._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[UserCapServiceImpl])
trait UserCapService {

  def isAccountCreateAllowed(): Future[Boolean]

  def update(): Future[Unit]

}

object UserCapService {
  val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}

@Singleton
class UserCapServiceImpl @Inject() (userCapStore: UserCapStore, configuration: Configuration) extends UserCapService with Logging {

  private val isDailyCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.daily.enabled")

  private val isTotalCapEnabled = configuration.underlying.getBoolean("microservice.user-cap.total.enabled")

  private val dailyCap = configuration.underlying.getInt("microservice.user-cap.daily.limit")

  private val totalCap = configuration.underlying.getInt("microservice.user-cap.total.limit")

  require(dailyCap >= 0 && totalCap >= 0)

  private def check: UserCap ⇒ Boolean = { userCap ⇒
    (isTotalCapEnabled, isDailyCapEnabled) match {
      case (true, true) ⇒
        if (userCap.isTodaysRecord) {
          userCap.dailyCount < dailyCap && userCap.totalCount < totalCap
        } else {
          userCap.totalCount < totalCap
        }

      case (true, false) ⇒ userCap.totalCount < totalCap

      case (false, true) ⇒
        if (userCap.isTodaysRecord) {
          userCap.dailyCount < dailyCap
        } else {
          true
        }

      case (false, false) ⇒ true
    }
  }

  override def isAccountCreateAllowed: Future[Boolean] = {

    if (dailyCap === 0 || totalCap === 0) {
      toFuture(false)
    } else {
      userCapStore.getOne().map {
        case Some(userCap) ⇒ check(userCap)
        case None          ⇒ true
      }.recover {
        case NonFatal(e) ⇒
          logger.warn("error checking account cap", e)
          true
      }
    }
  }

  override def update(): Future[Unit] = {
    val today = LocalDate.now()

    val formattedToday = dateFormat.format(today)

    val queryResult = userCapStore.getOne()

    queryResult.map[Unit] {
      case Some(userCap) ⇒
        val newRecord = (isTotalCapEnabled, isDailyCapEnabled) match {
          case (false, false) ⇒ UserCap(formattedToday, 0, 0)
          case (_, _) ⇒
            val c = if (userCap.isPreviousRecord) 1 else userCap.dailyCount + 1
            UserCap(formattedToday, c, userCap.totalCount + 1)
        }
        userCapStore.upsert(newRecord).map(_ ⇒ ())
      case None ⇒
        val newRecord = (isTotalCapEnabled, isDailyCapEnabled) match {
          case (false, false) ⇒ UserCap(formattedToday, 0, 0)
          case (_, _)         ⇒ UserCap(formattedToday, 1, 1)
        }
        userCapStore.upsert(newRecord).map(_ ⇒ ())
    }.recover {
      case e ⇒ logger.warn("error updating the account cap", e)
    }
  }
}

