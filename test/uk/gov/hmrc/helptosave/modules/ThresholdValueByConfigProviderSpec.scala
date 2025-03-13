/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.test.Helpers.running
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Environment}

class ThresholdValueByConfigProviderSpec extends AnyWordSpec with Matchers with GuiceOneAppPerTest {
  def buildTestApp(isMDTPConfigActive: Boolean, effectiveDate: String): Application = {
    GuiceApplicationBuilder()
      .configure(
        "mdtp-threshold.active" -> isMDTPConfigActive,
        "mdtp-threshold.effective-date" -> effectiveDate,
        "metrics.jvm" -> false
      )
      .bindings(new UCThresholdModule(Environment.simple(), Configuration.empty))
      .build()
  }

  "ThresholdValueByConfigProvider" should {
    "use MDTP threshold config if today is after effective date and MDTP threshold config is active" in {
      val app = buildTestApp(isMDTPConfigActive = true, effectiveDate = "2000-01-01")

      running(app) {
        val thresholdOrchestrator = app.injector.instanceOf[ThresholdOrchestrator]
        thresholdOrchestrator shouldBe a[MDTPThresholdOrchestrator]
      }
    }

    "use UC ThresholdManager actor if MDTP threshold config is inactive (regardless of effective date)" in {
      val app = buildTestApp(isMDTPConfigActive = false, effectiveDate = "2000-01-01")

      running(app) {
        val thresholdOrchestrator = app.injector.instanceOf[ThresholdOrchestrator]
        thresholdOrchestrator shouldBe a[UCThresholdOrchestrator]
      }
    }

    "use UC ThresholdManager actor if MDTP threshold config is active but today is before effective date" in {
      val app = buildTestApp(isMDTPConfigActive = true, effectiveDate = "2999-12-31")

      running(app) {
        val thresholdOrchestrator = app.injector.instanceOf[ThresholdOrchestrator]
        thresholdOrchestrator shouldBe a[UCThresholdOrchestrator]
      }
    }
  }
}
