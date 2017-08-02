package uk.gov.hmrc.helptosave

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.helptosave.models.MissingUserInfo.{Surname, GivenName}
import uk.gov.hmrc.helptosave.models.MissingUserInfos
import uk.gov.hmrc.helptosave.support.{IntegrationSpec, InvitationActions}


class MissingMandatoryData extends IntegrationSpec with InvitationActions with ScalaFutures {

  // To do: convert to Gherkin/Cucumber
  feature("An applicant has all required mandatory data fields") {

    scenario("An eligible applicant can proceed with their application") {

      Given("an applicant with all required details")
      val nino = "AG010xxxx" //need to write in correct NINOs for tests to pass
      val authCode = "AG010xxxx"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(nino, authCode)

      Then("I see that the surname is missing")
      checkEligibilityResponse.status shouldBe OK
    }

  }

  feature("An applicant has missing mandatory data fields") {

    scenario("An applicant with no associated surname CANNOT proceed with their application") {

      Given("an applicant with no associated surname")
      val nino = "AE120xxxx"
      val authCode = "AE120xxxx"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(nino, authCode)

      Then("I see that the surname is missing")
      checkEligibilityResponse.status shouldBe OK
      val missingUserInfos = MissingUserInfos(Set(Surname))
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Surname"]}}""")
    }

  }

  feature("An applicant has missing mandatory data fields") {

    scenario("An applicant with no associated forename CANNOT proceed with their application") {

      Given("an applicant with no associated forename")
      val nino = "AE120xxxx"
      val authCode = "AE120xxxx"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(nino, authCode)

      Then("I see that the surname is missing")
      checkEligibilityResponse.status shouldBe OK
      val missingUserInfos = MissingUserInfos(Set(GivenName))
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["GivenName"]}}""")
    }

  }



}
