package uk.gov.hmrc.helptosave.connectors

import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.models.NSIUserInfo
import uk.gov.hmrc.helptosave.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FrontendConnectorImpl])
trait FrontendConnector {

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class FrontendConnectorImpl @Inject()(http: WSHttp)(implicit hc: HeaderCarrier)
  extends FrontendConnector with ServicesConfig with Logging {

  val createAccountURL: String = getString("microservice.services.help-to-save-frontend.url")

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    http.post(createAccountURL, userInfo)(NSIUserInfo.nsiUserInfoFormat, hc, ec)
  }
}
