package uk.gov.hmrc.agentsmtdfiinvitation.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.libs.ws.WSClient

trait ActionsSupport extends IntegrationSpec with GuiceOneServerPerSuite with Status with ScalaFutures {

  val url = "http://localhost:9426/agents-mtdfi-invitation"

  override lazy val port: Int = 9426

  val wsClient = app.injector.instanceOf[WSClient]

}
