package uk.gov.hmrc.helptosave.support

import org.scalatest.Suite
import play.api.libs.ws.WSResponse

trait InvitationActions extends ActionsSupport {

  this: Suite =>

  def checkEligibility ( nino: String, authCode: String): WSResponse = {
    wsClient
      .url(s"$url?nino=$nino&oauthAuthorisationCode=$authCode")
      .get()
      .futureValue
  }

}
