package uk.gov.hmrc.helptosave

import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.{CitizenDetailsAddress, CitizenDetailsPerson, CitizenDetailsResponse}
import uk.gov.hmrc.helptosave.connectors.UserDetailsConnector.UserDetailsResponse
import uk.gov.hmrc.helptosave.models.MissingUserInfo.{Email, Surname}
import uk.gov.hmrc.helptosave.models.{Address, UserInfo}
import uk.gov.hmrc.helptosave.services.UserInfoService.UserInfoServiceError.MissingUserInfos
import uk.gov.hmrc.helptosave.support.WiremockSupport
import uk.gov.hmrc.helptosave.util.NINO


class MissingMandatoryData extends WordSpec
  with Matchers
  with ScalaFutures
  with WiremockSupport
  with BeforeAndAfterAll
  with BeforeAndAfterEach
 with GuiceOneServerPerSuite {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  override lazy val port: Int = 7001

  val wsClient = app.injector.instanceOf[WSClient]

  val url = "http://localhost:7001/help-to-save/eligibility-check"

  def decode(encodedNINO: String): NINO = {
    new String(Base64.getDecoder.decode(encodedNINO))
  }

  def checkEligibility(encodedNino: String, userDetailsURI: String): WSResponse = {
    wsClient
      .url(s"$url?nino=${decode(encodedNino)}&userDetailsURI=${URLEncoder.encode(userDetailsURI)}")
      .get()
      .futureValue
  }

  def stubCall(url: String, json: String) = {
    wireMockServer.stubFor(
      get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(json)
        )
    )
  }

  // To do: convert to Gherkin/Cucumber
  "Checking if a user can create an account" when {

    "an applicant is eligible" when {

      "user details and citizen details return all the required fields" must {

        "return with a 200" in {
          val encodedNino = "QUcwMTAxMjND"
          val citizenDetailsPerson = CitizenDetailsPerson(Some("Sarah"), Some("Smith"), Some(LocalDate.of(1999, 12, 12)))
          val citizenDetailsAddress = CitizenDetailsAddress(Some("line 1"), Some("line 2"), Some("line 3"), Some("line 4"),
            Some("line 5"), Some("BN43 XXX"), Some("GB"))
          val citizenDetailsResponse = CitizenDetailsResponse(Some(citizenDetailsPerson), Some(citizenDetailsAddress))
          val userDetails = UserDetailsResponse("Sarah", Some("Smith"), Some("email@gmail.com"), Some(LocalDate.of(1999, 12, 12)))
          val expected: UserInfo = UserInfo("Sarah", "Smith", decode(encodedNino), LocalDate.of(1999, 12, 12), "email@gmail.com",
            Address(List("line 1", "line 2", "line 3", "line 4", "line 5"), Some("BN43 XXX"), Some("GB")))

          stubCall("/help-to-save-stub/eligibilitycheck.*", """{ "isEligible" : true }""")
          stubCall("/user-details.*", Json.toJson(userDetails).toString())
          stubCall(".*/citizen-details.*", Json.toJson(citizenDetailsResponse).toString)

          val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, "http://localhost:7002/user-details/hello")

          checkEligibilityResponse.status shouldBe OK
          val json = Json.toJson(expected)
          (checkEligibilityResponse.json \ "result").get shouldBe json
        }
      }
    }

  }


  "Checking an applicant cannot create an account" when {

    "An applicant has no associated surname so CANNOT proceed with their application" must {

      "return with a 200 result and a response showing the surname is missing" in {
        val encodedNino = "QUUxMjAxMjNB"
        val citizenDetailsPerson = CitizenDetailsPerson(Some("Sarah"), None, Some(LocalDate.of(1999, 12, 12)))
        val citizenDetailsAddress = CitizenDetailsAddress(Some("line 1"), Some("line 2"), Some("line 3"), Some("line 4"),
          Some("line 5"), Some("BN43 XXX"), Some("GB"))
        val citizenDetailsResponse = CitizenDetailsResponse(Some(citizenDetailsPerson), Some(citizenDetailsAddress))
        val userDetails = UserDetailsResponse("Sarah", None, Some("email@gmail.com"), Some(LocalDate.of(1999, 12, 12)))

        stubCall("/help-to-save-stub/eligibilitycheck.*", """{ "isEligible" : true }""")
        stubCall("/user-details.*", Json.toJson(userDetails).toString())
        stubCall(".*/citizen-details.*", Json.toJson(citizenDetailsResponse).toString)

        val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, "http://localhost:7002/user-details/")

        checkEligibilityResponse.status shouldBe OK
        val missingUserInfos = MissingUserInfos(Set(Surname))
        checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Surname"]}}""")
      }
    }
  }


  "Checking an applicant cannot create an account" when {

    "An applicant has no associated email should be able to proceed with their application" must {

      "return with a 200 result and a response showing the email address is missing" in {
        val encodedNino = "QUUxMzAxMjNC"
        val citizenDetailsPerson = CitizenDetailsPerson(Some("Sarah"), Some("Smith"), Some(LocalDate.of(1999,12,12)))
        val citizenDetailsAddress = CitizenDetailsAddress(Some("line 1"), Some("line 2"), Some("line 3"), Some("line 4"),
          Some("line 5"), Some("BN43 XXX"), Some("GB"))
        val citizenDetailsResponse = CitizenDetailsResponse(Some(citizenDetailsPerson), Some(citizenDetailsAddress))
        val userDetails = UserDetailsResponse("Sarah", None, None, Some(LocalDate.of(1999,12,12)))

        stubCall("/help-to-save-stub/eligibilitycheck.*", """{ "isEligible" : true }""")
        stubCall("/user-details.*", Json.toJson(userDetails).toString())
        stubCall(".*/citizen-details.*", Json.toJson(citizenDetailsResponse).toString)

        val checkEligibilityResponse: WSResponse = checkEligibility(encodedNino, "http://localhost:7002/user-details/")
        checkEligibilityResponse.status shouldBe OK
        val missingUserInfos = MissingUserInfos(Set(Email))
        checkEligibilityResponse.json shouldBe Json.parse("""{"result":{"missingInfo":["Email"]}}""")
        }
      }
    }

}