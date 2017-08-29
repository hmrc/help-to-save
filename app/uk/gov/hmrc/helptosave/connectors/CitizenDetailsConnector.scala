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

import java.time.LocalDate

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{Format, Json, Reads}
import uk.gov.hmrc.helptosave.config.WSHttp
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

/**
 * A connector which connects to the `citizen-details` microservice to obtain
 * a person's address
 */
@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {

  def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse]
}

object CitizenDetailsConnector {

  case class CitizenDetailsPerson(firstName:   Option[String],
                                  lastName:    Option[String],
                                  dateOfBirth: Option[LocalDate])

  case class CitizenDetailsAddress(line1:    Option[String],
                                   line2:    Option[String],
                                   line3:    Option[String],
                                   line4:    Option[String],
                                   line5:    Option[String],
                                   postcode: Option[String],
                                   country:  Option[String])

  case class CitizenDetailsResponse(person: Option[CitizenDetailsPerson], address: Option[CitizenDetailsAddress])

  implicit val addressFormat: Format[CitizenDetailsAddress] = Json.format[CitizenDetailsAddress]

  implicit val personFormat: Format[CitizenDetailsPerson] = Json.format[CitizenDetailsPerson]

  implicit val citizenDetailsResponseFormat: Format[CitizenDetailsResponse] = Json.format[CitizenDetailsResponse]
}

@Singleton
class CitizenDetailsConnectorImpl extends CitizenDetailsConnector with ServicesConfig {

  val citizenDetailsBaseURL: String = baseUrl("citizen-details")

  def citizenDetailsURI(nino: NINO): String = s"$citizenDetailsBaseURL/citizen-details/$nino/designatory-details"

  val http = new WSHttp

  override def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse] =
    EitherT[Future, String, CitizenDetailsResponse](
      http.get(citizenDetailsURI(nino)).map(response ⇒
        if (response.status == 200) {
          response.parseJson[CitizenDetailsResponse]
        } else {
          Left(s"Citizen details response came back with status ${response.status}. Response body was ${response.body}")
        }
      ).recover{
        case e ⇒ Left(s"Error calling citizen details service: ${e.getMessage}")
      }
    )
}
