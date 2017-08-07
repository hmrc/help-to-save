package uk.gov.hmrc.helptosave

import java.util.Base64

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.helptosave.models.MissingUserInfo.{Email, GivenName, Surname}
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.MissingUserInfos
import uk.gov.hmrc.helptosave.support.{IntegrationSpec, InvitationActions}
import uk.gov.hmrc.helptosave.util.NINO


class MissingMandatoryData extends IntegrationSpec with InvitationActions with ScalaFutures {

  def decode(encodedNINO: String): NINO = {
    new String(Base64.getDecoder.decode(encodedNINO))
  }

  // To do: convert to Gherkin/Cucumber
  feature("An applicant has all required mandatory data fields") {

    scenario("An eligible applicant can proceed with their application") {

      Given("an applicant with all required details")
      val encodedNino = "QUcwMTAxMjND"
      val authCode = "QUcwMTAxMjND"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(decode(encodedNino), authCode)

      Then("I see that there are no missing fields")
      checkEligibilityResponse.status shouldBe OK
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":[]}}""")
    }

  }


  feature("An applicant has missing mandatory data fields") {

    scenario("An applicant with no associated surname CANNOT proceed with their application") {

      Given("an applicant with no associated surname")
      val encodedNino = "QUUxMjAxMjNB"
      val authCode = "QUUxMjAxMjNB"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(decode(encodedNino), authCode)

      Then("I see that the surname is missing")
      checkEligibilityResponse.status shouldBe OK
      val missingUserInfos = MissingUserInfos(Set(Surname))
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Surname"]}}""")
    }

  }

  feature("An applicant has missing mandatory data fields") {

    scenario("An applicant with no associated forename CANNOT proceed with their application") {

      Given("an applicant with no associated forename")
      val encodedNino = "QUUxMjAxMjNC"
      val authCode = "QUUxMjAxMjNC"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(decode(encodedNino), authCode)

      Then("I see that the forename is missing")
      checkEligibilityResponse.status shouldBe OK
      val missingUserInfos = MissingUserInfos(Set(GivenName))
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["GivenName"]}}""")
    }

  }

  feature("An applicant has missing not mandatory data fields") {

    scenario("An applicant with no associated email should be able to proceed with their application") {

      Given("an applicant with no associated email address")
      val encodedNino = "QUUxMzAxMjNC"
      val authCode = "QUUxMzAxMjNC"

      When("I check whether the applicant is eligible for HtS")
      val checkEligibilityResponse : WSResponse = checkEligibility(decode(encodedNino), authCode)

      Then("I see that the email is missing")
      checkEligibilityResponse.status shouldBe OK
      val missingUserInfos = MissingUserInfos(Set(Email))
      checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Email"]}}""")
    }

  }

}
