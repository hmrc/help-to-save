package uk.gov.hmrc.helptosave

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.helptosave.support.{FakeRelationshipService, IntegrationSpec, InvitationActions}

class MissingMandatoryData extends IntegrationSpec with InvitationActions with FakeRelationshipService {

  // To do: convert to Gherkin/Cucumber
  feature("An applicant has missing mandatory data fields") {

    scenario("An applicant with no associated surname CANNOT proceed with their application") {

      Given("an applicant with no associated surname")
      val nino = "AG01xxxxx"
      val authCode = "AG01xxxxx"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(nino, authCode)

      Then("I see that the surname is missing")
      checkEligibilityResponse.status shouldBe OK
    }

  }

}
