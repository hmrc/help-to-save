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

package uk.gov.hmrc.helptosave.metrics

import com.codahale.metrics.{Counter, Timer}
import com.google.inject.{Inject, Singleton}

import scala.annotation.tailrec

@Singleton
class Metrics @Inject() (metrics: com.kenshoo.play.metrics.Metrics) {

  protected def timer(name: String): Timer = metrics.defaultRegistry.timer(name)

  protected def counter(name: String): Counter = metrics.defaultRegistry.counter(name)

  val itmpEligibilityCheckTimer: Timer = timer("backend.itmp-eligibility-check-time")

  val itmpEligibilityCheckErrorCounter: Counter = counter("backend.itmp-eligibility-check-error.count")

  val itmpSetFlagTimer: Timer = timer("backend.itmp-set-flag-time")

  val itmpSetFlagConflictCounter: Counter = counter("backend.itmp-set-flag-conflict.count")

  val itmpSetFlagErrorCounter: Counter = counter("backend.itmp-set-flag-error.count")

  val emailStoreUpdateTimer: Timer = timer("backend.email-store-update-time")

  val emailStoreUpdateErrorCounter: Counter = counter("backend.email-store-update-error.count")

  val emailStoreGetTimer: Timer = timer("backend.email-store-get-time")

  val emailStoreGetErrorCounter: Counter = counter("backend.email-store-get-error.count")

  val enrolmentStoreGetTimer: Timer = timer("backend.enrolment-store-get-time")

  val enrolmentStoreGetErrorCounter: Counter = counter("backend.enrolment-store-get-error.count")

  val enrolmentStoreUpdateTimer: Timer = timer("backend.enrolment-store-update-time")

  val enrolmentStoreUpdateErrorCounter: Counter = counter("backend.enrolment-store-update-error.count")

  val payePersonalDetailsTimer: Timer = timer("backend.paye-personal-details.time")

  val payePersonalDetailsErrorCounter: Counter = counter("backend.paye-personal-details-error.count")

  val getAccountTimer: Timer = timer("backend.get-account.time")

  val getAccountErrorCounter: Counter = counter("backend.get-account-error.count")
}

