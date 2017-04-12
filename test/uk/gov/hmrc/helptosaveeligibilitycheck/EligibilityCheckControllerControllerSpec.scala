package uk.gov.hmrc.helptosaveeligibilitycheck.controllers

import play.api.http.Status
import play.api.test.FakeRequest
import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.play.test.WithFakeApplication
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class EligibilityCheckControllerControllerSpec extends UnitSpec with WithFakeApplication{

  val fakeRequest = FakeRequest("GET", "/")


  "GET /" should {
    "return 200" in {
//      val result = EligibilityCheckController$.hello()(fakeRequest)
//      status(result) shouldBe Status.OK
    }
  }


}
