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

import cats.instances.future._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.helptosave.WSHttp
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.util.Result
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.helptosave.util.HttpResponseOps._

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[ApiTwentyFiveCConnectorImpl])
trait ApiTwentyFiveCConnector {
  def getAwards(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[List[Award]]
}

/**
  * Implements communication with help-to-save-stub
  */
@Singleton
class ApiTwentyFiveCConnectorImpl extends ServicesConfig with ApiTwentyFiveCConnector {

  private val helpToSaveStubURL: String = baseUrl("help-to-save-stub")

  private def serviceURL(nino: String) = s"help-to-save-stub/edh/wtc/$nino"

  private val http = new WSHttp

  def getAwards(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[List[Award]] =
    Result(http.get(s"$helpToSaveStubURL/${serviceURL(nino)}"))
      // subflatMap into the EitherT so we can conveniently return an Either rather than being
      // forced to return an EitherT in the body
      .subflatMap { response ⇒
      response.parseJson[ApiTwentyFiveCValues].map(_.awards)
    }
}
