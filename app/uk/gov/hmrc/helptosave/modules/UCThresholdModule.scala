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
import uk.gov.hmrc.helptosave.actors.{UCThresholdConnectorProxy, UCThresholdMongoProxy, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.UCThresholdConnector
import uk.gov.hmrc.helptosave.repo.ThresholdStore
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting

class UCThresholdModule extends AbstractModule {

  override def configure() = bind(classOf[UCThresholdOrchestrator]).asEagerSingleton()

}

@Singleton
class UCThresholdOrchestrator @Inject() (system:            ActorSystem,
                                         pagerDutyAlerting: PagerDutyAlerting,
                                         configuration:     Configuration,
                                         connector:         UCThresholdConnector,
                                         store:             ThresholdStore) {

  private val connectorProxy: ActorRef = system.actorOf(UCThresholdConnectorProxy.props(connector))
  private val mongoProxy: ActorRef = system.actorOf(UCThresholdMongoProxy.props(store))

  val thresholdHandler: ActorRef = system.actorOf(UCThresholdManager.props(
    connectorProxy,
    mongoProxy,
    pagerDutyAlerting,
    system.scheduler,
    configuration.underlying
  ))

}
