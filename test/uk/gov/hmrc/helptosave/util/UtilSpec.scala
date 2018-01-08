package uk.gov.hmrc.helptosave.util

import org.scalatest.Matchers
import uk.gov.hmrc.play.test.UnitSpec

class UtilSpec extends Matchers with UnitSpec {

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
