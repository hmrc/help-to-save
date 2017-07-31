package uk.gov.hmrc.agentsmtdfiinvitation.support

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

trait IntegrationSpec extends FeatureSpec with GivenWhenThen with Matchers with Eventually {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))


}
