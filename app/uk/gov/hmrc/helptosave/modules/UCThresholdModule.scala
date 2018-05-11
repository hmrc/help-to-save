package uk.gov.hmrc.helptosave.modules

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{ThresholdConnectorProxy, ThresholdMongoProxy, UCThresholdManager}
import uk.gov.hmrc.helptosave.connectors.ThresholdConnector
import uk.gov.hmrc.helptosave.repo.ThresholdStore
import uk.gov.hmrc.helptosave.util.PagerDutyAlerting

class UCThresholdModule extends AbstractModule {

  override def configure() = bind(classOf[UCThresholdOrchestrator]).asEagerSingleton()

}


@Singleton
class UCThresholdOrchestrator @Inject()(system: ActorSystem,
                                        pagerDutyAlerting: PagerDutyAlerting,
                                        configuration: Configuration,
                                        connector: ThresholdConnector,
                                        store: ThresholdStore) {

  private val connectorProxy: ActorRef = system.actorOf(ThresholdConnectorProxy.props(connector))
  private val mongoProxy: ActorRef = system.actorOf(ThresholdMongoProxy.props(store))


  val thresholdHandler: ActorRef = system.actorOf(UCThresholdManager.props(
    connectorProxy,
    mongoProxy,
    pagerDutyAlerting,
    system.scheduler,
    configuration.underlying
  ))

}