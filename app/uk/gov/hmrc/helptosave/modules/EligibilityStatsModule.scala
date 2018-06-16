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

import java.time.{Clock, ZoneId}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.helptosave.actors.{EligibilityStatsActor, TimeCalculatorImpl}
import uk.gov.hmrc.helptosave.services.EligibilityStatsService
import uk.gov.hmrc.helptosave.util.Logging

class EligibilityStatsModule extends AbstractModule {
  override def configure() = bind(classOf[EligibilityStatsProvider]).to(classOf[EligibilityStatsProviderImpl]).asEagerSingleton()
}

trait EligibilityStatsProvider {
  val esActor: ActorRef
}

@Singleton
class EligibilityStatsProviderImpl @Inject() (system:                  ActorSystem,
                                              configuration:           Configuration,
                                              eligibilityStatsService: EligibilityStatsService) extends EligibilityStatsProvider with Logging {

  val enabled: Boolean = configuration.underlying.getBoolean("eligibility-stats.enabled")

  private val timeCalculator = {
    val clock = Clock.system(ZoneId.of(configuration.underlying.getString("eligibility-stats.timezone")))
    new TimeCalculatorImpl(clock)
  }

  val esActor: ActorRef = if (enabled) {
    logger.info("Eligibility Stats behaviour enabled: starting EligibilityStatsActor")
    system.actorOf(EligibilityStatsActor.props(
      system.scheduler,
      configuration.underlying,
      timeCalculator,
      eligibilityStatsService
    ))
  } else {
    logger.info("Eligibility Stats behaviour not enabled: not starting EligibilityStatsActor")
    // ActorRef.noSender is actually null - we're abusing the use of
    // the value here to support the temporary enabled/disabled behaviour
    ActorRef.noSender
  }

}
