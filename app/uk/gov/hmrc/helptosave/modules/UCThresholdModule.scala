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

package uk.gov.hmrc.helptosave.modules

import com.google.inject.{AbstractModule, Inject, Singleton}
import org.apache.pekko.actor.{ActorRef, ActorSystem, PoisonPill}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{TimeCalculatorImpl, UCThresholdConnectorProxyActor, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting}
import uk.gov.hmrc.helptosave.actors.UCThresholdManager.{GetThresholdValue, GetThresholdValueResponse}

import java.time.{Clock, ZoneId}
import org.apache.pekko.pattern.ask
import uk.gov.hmrc.helptosave.config.AppConfig

import javax.inject.Provider
import scala.concurrent.{ExecutionContext, Future}

class UCThresholdModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UCThresholdOrchestrator]).asEagerSingleton()
    bind(classOf[MDTPThresholdOrchestrator]).asEagerSingleton()

    bind(classOf[ThresholdOrchestrator]).toProvider(classOf[ThresholdValueByConfigProvider]).asEagerSingleton()
  }
}

trait ThresholdOrchestrator {
  def getValue: Future[Option[Double]]
}

@Singleton
class UCThresholdOrchestrator @Inject() (
  system: ActorSystem,
  pagerDutyAlerting: PagerDutyAlerting,
  configuration: Configuration,
  desConnector: DESConnector
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends ThresholdOrchestrator
    with Logging {
  private lazy val connectorProxy: ActorRef =
    system.actorOf(UCThresholdConnectorProxyActor.props(desConnector, pagerDutyAlerting))

  private val timeCalculator = {
    val clock = Clock.system(ZoneId.of(configuration.underlying.getString("uc-threshold.update-timezone")))
    new TimeCalculatorImpl(clock)
  }

  protected val thresholdManager: ActorRef = {
    logger.info("Starting UCThresholdManager")
    system.actorOf(
      UCThresholdManager.props(
        connectorProxy,
        pagerDutyAlerting,
        system.scheduler,
        timeCalculator,
        configuration
      )
    )
  }

  override def getValue: Future[Option[Double]] =
    thresholdManager
      .ask(GetThresholdValue)(appConfig.thresholdAskTimeout)
      .mapTo[GetThresholdValueResponse]
      .map(r => r.result)

  def stop(): Unit =
    thresholdManager ! PoisonPill
}

@Singleton
class MDTPThresholdOrchestrator @Inject() (appConfig: AppConfig) extends ThresholdOrchestrator {
  override def getValue: Future[Option[Double]] =
    Future.successful(Some(appConfig.mdtpThresholdAmount))
}

@Singleton
class ThresholdValueByConfigProvider @Inject() (
  appConfig: AppConfig,
  desThresholdProvider: Provider[UCThresholdOrchestrator],
  mdtpThresholdProvider: Provider[MDTPThresholdOrchestrator]
) extends Provider[ThresholdOrchestrator] {

  def get: ThresholdOrchestrator =
    if appConfig.useMDTPThresholdConfig then {
      desThresholdProvider.get().stop()
      mdtpThresholdProvider.get()
    } else desThresholdProvider.get()
}
