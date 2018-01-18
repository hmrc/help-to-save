package uk.gov.hmrc.helptosave.connectors

import java.time.LocalDate

import play.api.libs.json.Writes
import play.mvc.Http.Status.{BAD_REQUEST, CREATED}
import uk.gov.hmrc.helptosave.models.NSIUserInfo
import uk.gov.hmrc.helptosave.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosave.util.toFuture
import uk.gov.hmrc.helptosave.utils.TestSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class FrontendConnectorSpec extends TestSupport {

  lazy val frontendConnector = new FrontendConnectorImpl(mockHttp)
  val createAccountURL: String = "http://localhost:7002/nsi-services/account"

  val userInfo: NSIUserInfo =
    NSIUserInfo(
      "forename",
      "surname",
      LocalDate.now(),
      "nino",
      ContactDetails("address1", "address2", Some("address3"), Some("address4"), Some("address5"), "postcode", Some("GB"), Some("phoneNumber"), "commPref"),
      "regChannel"
    )

  def mockCreateAccountResponse(userInfo: NSIUserInfo)(response: Future[HttpResponse]) =
    (mockHttp.post(_: String, _: NSIUserInfo, _: Map[String, String])(_: Writes[NSIUserInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountURL, userInfo, Map.empty[String, String], *, *, *)
      .returning(response)

  "The FrontendConnector" when {
    "creating account" must {
      "handle success response from frontend" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(CREATED)))
        val result = await(frontendConnector.createAccount(userInfo))

        result.status shouldBe CREATED
      }

      "handle bad_request response from frontend" in {

        mockCreateAccountResponse(userInfo)(toFuture(HttpResponse(BAD_REQUEST)))
        val result = await(frontendConnector.createAccount(userInfo))

        result.status shouldBe BAD_REQUEST
      }
    }
  }
}
