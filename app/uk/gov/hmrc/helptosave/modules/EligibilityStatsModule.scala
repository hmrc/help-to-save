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

import org.apache.pekko.actor.{ActorRef, ActorSystem, PoisonPill}
import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.helptosave.actors.{EligibilityStatsActor, EligibilityStatsParser}
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.repo.EligibilityStatsStore
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.helptosave.util.lock.Lock
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import scala.concurrent.duration.FiniteDuration

class EligibilityStatsModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[EligibilityStatsProvider]).to(classOf[EligibilityStatsProviderImpl]).asEagerSingleton()
}

trait EligibilityStatsProvider {
  def esActor(): ActorRef
}

@Singleton
class EligibilityStatsProviderImpl @Inject()(
  system: ActorSystem,
  configuration: Configuration,
  eligibilityStatsStore: EligibilityStatsStore,
  eligibilityStatsParser: EligibilityStatsParser,
  mongoLockRepository: MongoLockRepository,
  lifecycle: ApplicationLifecycle,
  metrics: Metrics)
    extends EligibilityStatsProvider with Logging {
  private val name = "eligibility-stats"
  private val enabled: Boolean = configuration.underlying.getBoolean(s"$name.enabled")

  private val lockDuration: FiniteDuration = configuration.get[FiniteDuration](s"$name.lock-duration")

  def esActor(): ActorRef =
    if (enabled) {
      logger.info("Eligibility Stats behaviour enabled: starting EligibilityStatsActor")
      system.actorOf(
        EligibilityStatsActor.props(
          system.scheduler,
          configuration,
          eligibilityStatsStore,
          eligibilityStatsParser,
          metrics
        ))
    } else {
      logger.info("Eligibility Stats behaviour not enabled: not starting EligibilityStatsActor")
      // ActorRef.noSender is actually null - we're abusing the use of
      // the value here to support the temporary enabled/disabled behaviour
      ActorRef.noSender
    }

  // make sure we only have one instance of the eligibility stats running across
  // multiple instances of the application in the same environment
  private lazy val lockedEligibilityStats =
    system.actorOf(
      Lock.props[Option[ActorRef]](
        mongoLockRepository,
        s"$name",
        lockDuration,
        system.scheduler,
        None,
        _.fold(Some(esActor()))(Some(_)),
        _.flatMap { ref =>
          ref ! PoisonPill
          None
        },
        lifecycle),
      s"$name-lock"
    )

  // start the eligibility only if it is enabled
  if (enabled) {
    val _ = lockedEligibilityStats
  } else {
    logger.info("Eligibility Stats behaviour not enabled")
  }
}
