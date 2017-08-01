package uk.gov.hmrc.helptosave.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.libs.ws.WSClient

trait ActionsSupport extends IntegrationSpec with GuiceOneServerPerSuite with Status with ScalaFutures {

  val url = "http://localhost:7001/help-to-save/eligibility-check"

  override lazy val port: Int = 7001

  val wsClient = app.injector.instanceOf[WSClient]

}
