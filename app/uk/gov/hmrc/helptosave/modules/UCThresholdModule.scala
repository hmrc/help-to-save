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

import java.time.{Clock, ZoneId}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{TimeCalculatorImpl, UCThresholdConnectorProxyActor, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.DESConnector
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting}

class UCThresholdModule extends AbstractModule {

  override def configure() = bind(classOf[ThresholdManagerProvider]).to(classOf[UCThresholdOrchestrator]).asEagerSingleton()

}

trait ThresholdManagerProvider {
  val thresholdManager: ActorRef
}

@Singleton
class UCThresholdOrchestrator @Inject() (system:            ActorSystem,
                                         pagerDutyAlerting: PagerDutyAlerting,
                                         configuration:     Configuration,
                                         desConnector:      DESConnector) extends ThresholdManagerProvider with Logging {

  private lazy val connectorProxy: ActorRef = system.actorOf(UCThresholdConnectorProxyActor.props(desConnector, pagerDutyAlerting))

  private val timeCalculator = {
    val clock = Clock.system(ZoneId.of(configuration.underlying.getString("uc-threshold.update-timezone")))
    new TimeCalculatorImpl(clock)
  }

  val thresholdManager: ActorRef = {
    logger.info("Starting UCThresholdManager")
    system.actorOf(UCThresholdManager.props(
      connectorProxy,
      pagerDutyAlerting,
      system.scheduler,
      timeCalculator,
      configuration.underlying
    ))
  }

}
