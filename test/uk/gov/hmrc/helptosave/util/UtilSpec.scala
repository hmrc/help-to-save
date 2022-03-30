/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.util

class UtilSpec extends UnitSpec {

  "Util.maskNino(*)" must {
    "mask ninos in the given string" in {
        def errorJson(nino: String) =
          s"""eligibility response body from DES is:
          <am:fault xmlns:am="http://wso2.org/apimanager">
            <am:code>404</am:code>
            <am:type>Status report</am:type>
            <am:message>Not Found</am:message>
            <am:description>The requested resource (/help-to-save/eligibility-check/$nino) is not available.</am:description>
          </am:fault>"""

      val original = errorJson("JA553215D")
      val expected = errorJson("<NINO>")

      maskNino(original) shouldBe expected
    }

    "return the same string if no nino match found" in {
      maskNino("AE11111") shouldBe "AE11111"
      maskNino("ABCDEF123456") shouldBe "ABCDEF123456"
    }
  }

}
