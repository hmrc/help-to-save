/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosave.connectors

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

private[connectors] case class EligibilityResult(isEligible: Boolean) extends AnyVal

object EligibilityResult {
  implicit val format: Format[EligibilityResult] = Json.format[EligibilityResult]
}

@ImplementedBy(classOf[EligibilityCheckConnectorImpl])
trait EligibilityCheckConnector {
  def isEligible(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Boolean]
}

@Singleton
class EligibilityCheckConnectorImpl extends EligibilityCheckConnector with ServicesConfig {

  val helpToSaveStubURL: String = baseUrl("help-to-save-stub")

  def serviceURL(nino: String) = s"help-to-save-stub/eligibilitycheck/$nino"

  val http = new WSHttp

  override def isEligible(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Boolean] =
    EitherT[Future, String, Boolean](http.get(s"$helpToSaveStubURL/${serviceURL(nino)}").map {
      _.parseJson[EligibilityResult].right.map(_.isEligible)
    }.recover {
      case e ⇒
        Left(s"Error encountered when checking eligibility: ${e.getMessage}")
    })
}
