/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models

import play.api.libs.json.{Format, Json}

import java.time.{LocalDate, ZoneId}
import java.time.format.DateTimeFormatter

case class UserCap(date: LocalDate = LocalDate.now(UserCap.utcZone), dailyCount: Int, totalCount: Int) {

  def isTodaysRecord: Boolean = LocalDate.now(UserCap.utcZone).isEqual(date)

  def isPreviousRecord: Boolean = !isTodaysRecord
}

object UserCap {
  val utcZone: ZoneId               = ZoneId.of("Z")
  val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def apply(dailyCount: Int, totalCount: Int): UserCap =
    new UserCap(LocalDate.now(utcZone), dailyCount, totalCount)

  implicit val userCapFormat: Format[UserCap] = Json.format[UserCap]
}
