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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import play.mvc.Http.Status.{CONFLICT, OK}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosave.util.{Logging, NINO, Result}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@ImplementedBy(classOf[ITMPEnrolmentConnectorImpl])
trait ITMPEnrolmentConnector {

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class ITMPEnrolmentConnectorImpl @Inject() (http: WSHttp, metrics: Metrics) extends ITMPEnrolmentConnector with ServicesConfig with Logging {

  import uk.gov.hmrc.helptosave.connectors.ITMPEnrolmentConnectorImpl._

  val itmpEnrolmentURL: String = baseUrl("itmp-enrolment")

  def url(nino: NINO): String = s"$itmpEnrolmentURL/set-enrolment-flag/$nino"

  override def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT({
      val timerContext = metrics.itmpSetFlagTimer.time()

      http.post(url(nino), PostBody(), Seq.empty[(String, String)])
        .map[Either[String, Unit]] { response ⇒
          val time = timerContext.stop()

          response.status match {
            case OK ⇒
              logger.info(s"ITMP HtS flag successfully set (time: ${nanosToPrettyString(time)})")
              Right(())

            case CONFLICT ⇒
              metrics.itmpSetFlagConflictCounter.inc()
              logger.warn(s"Tried to set ITMP HtS flag even though it was already set - proceeding as normal  (time: ${nanosToPrettyString(time)})")
              Right(())

            case other ⇒
              metrics.itmpSetFlagErrorCounter.inc()
              Left(s"Received unexpected response status ($other) when trying to set ITMP flag  (time: ${nanosToPrettyString(time)})")
          }
        }
        .recover {
          case NonFatal(e) ⇒
            val time = timerContext.stop()
            metrics.itmpSetFlagErrorCounter.inc()
            Left(s"Encountered unexpected error while trying to set the ITMP flag: ${e.getMessage} (time: ${nanosToPrettyString(time)})")
        }
    })

}

object ITMPEnrolmentConnectorImpl {

  /** The information required in the body of the post request to set the ITMP flag */
  // TODO: put in the actual required details when they are known
  private[connectors] case class PostBody(tmp: String = "")

  implicit val postBodyFormat: Format[PostBody] = Json.format[PostBody]

}
