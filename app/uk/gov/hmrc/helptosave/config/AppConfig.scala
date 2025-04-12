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

package uk.gov.hmrc.helptosave.config

import com.google.inject.Singleton
import com.typesafe.config.ConfigRenderOptions
import play.api.libs.json.Json
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.helptosave.models.NINODeletionConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

@Singleton
class AppConfig @Inject() (
  val runModeConfiguration: Configuration,
  environment: Environment,
  servicesConfig: ServicesConfig
) {
  protected def mode: Mode = environment.mode

  val appName: String                   = servicesConfig.getString("appName")
  val ifEnabled: Boolean                = servicesConfig.getBoolean("feature.if.enabled")
  val desHeaders: Seq[(String, String)] = Seq(
    "Environment"   -> servicesConfig.getString("microservice.services.des.environment"),
    "Authorization" -> s"Bearer ${servicesConfig.getString("microservice.services.des.token")}"
  )

  val ifHeaders: Seq[(String, String)] = Seq(
    "Environment"   -> servicesConfig.getString("microservice.services.if.environment"),
    "Authorization" -> s"Bearer ${servicesConfig.getString("microservice.services.if.token")}"
  )

  val correlationIdHeaderName: String = servicesConfig.getString("microservice.correlationIdHeaderName")

  val thresholdAskTimeout: FiniteDuration = runModeConfiguration.get[FiniteDuration]("uc-threshold.ask-timeout")

  val mdtpThresholdAmount: Double = runModeConfiguration.get[Double]("mdtp-threshold.amount")

  val createAccountVersion: String = servicesConfig.getString("nsi.create-account.version")

  val barsUrl: String = servicesConfig.baseUrl("bank-account-reputation")

  val ninoDeletionConfig: String => Seq[NINODeletionConfig] = (configSuffix: String) =>
    runModeConfiguration.underlying
      .getObjectList(s"enrolment.$configSuffix")
      .asScala
      .flatMap { config =>
        Json.parse(config.render(ConfigRenderOptions.concise())).validate[NINODeletionConfig].asOpt
      }
      .toSeq

  def useMDTPThresholdConfig: Boolean = {
    val effectiveDate = LocalDate.parse(runModeConfiguration.get[String]("mdtp-threshold.effective-date"))
    val isActive      = runModeConfiguration.get[Boolean]("mdtp-threshold.active")

    isActive && !LocalDate.now().isBefore(effectiveDate)
  }
}
