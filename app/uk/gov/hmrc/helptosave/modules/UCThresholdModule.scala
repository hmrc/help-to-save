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

package uk.gov.hmrc.helptosave.modules

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{UCThresholdConnectorProxyActor, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.UCThresholdConnector
import uk.gov.hmrc.helptosave.util.{Logging, PagerDutyAlerting}

class UCThresholdModule extends AbstractModule {

  override def configure() = bind(classOf[UCThresholdOrchestrator]).asEagerSingleton()

}

@Singleton
class UCThresholdOrchestrator @Inject() (system:            ActorSystem,
                                         pagerDutyAlerting: PagerDutyAlerting,
                                         configuration:     Configuration,
                                         connector:         UCThresholdConnector) extends Logging {

  private lazy val connectorProxy: ActorRef = system.actorOf(UCThresholdConnectorProxyActor.props(connector))

  val enabled: Boolean = configuration.underlying.getBoolean("uc-threshold.enabled")

  val thresholdManager: ActorRef = if (enabled) {
    logger.info("UC threshold DES behaviour enabled: starting UCThresholdManager")
    system.actorOf(UCThresholdManager.props(
      connectorProxy,
      pagerDutyAlerting,
      system.scheduler,
      configuration.underlying
    ))
  } else {
    logger.info("UC threshold DES behaviour not enabled: not starting UCThresholdManager")
    // ActorRef.noSender is actually null - we're abusing the use of
    // the value here to support the temporary enabled/disabled behaviour
    ActorRef.noSender
  }

}
