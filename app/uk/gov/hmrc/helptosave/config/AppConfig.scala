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

package uk.gov.hmrc.helptosave.config

import configs.syntax._
import com.google.inject.Singleton
import javax.inject.Inject
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject() (override val runModeConfiguration: Configuration, val environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  val appName: String = getString("appName")

  val desHeaders: Map[String, String] = Map(
    "Environment" → getString("microservice.services.des.environment"),
    "Authorization" → s"Bearer ${getString("microservice.services.des.token")}"
  )

  val correlationIdHeaderName: String = getString("microservice.correlationIdHeaderName")

  val clientIdHeaderName: String = getString("microservice.clientIdHeaderName")

  val isUCThresholdEnabled: Boolean = getBoolean("uc-threshold.enabled")

  val thresholdAmount: Double = runModeConfiguration.underlying.get[Double]("uc-threshold.threshold-amount").value

  val thresholdAskTimeout: FiniteDuration = runModeConfiguration.underlying.get[FiniteDuration]("uc-threshold.ask-timeout").value

}
