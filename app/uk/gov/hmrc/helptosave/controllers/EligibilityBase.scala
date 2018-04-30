package uk.gov.hmrc.helptosave.controllers

import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.helptosave.services.EligibilityCheckService
import uk.gov.hmrc.helptosave.util.Logging.LoggerOps
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EligibilityBase extends Logging {

  val eligibilityCheckService: EligibilityCheckService

  def checkForNino(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, transformer: LogMessageTransformer): Future[Result] =
    eligibilityCheckService.getEligibility(nino).fold(
      {
        e ⇒
          logger.warn(s"Could not check eligibility due to $e", nino)
          InternalServerError
      }, r ⇒ Ok(Json.toJson(r))
    )
}
