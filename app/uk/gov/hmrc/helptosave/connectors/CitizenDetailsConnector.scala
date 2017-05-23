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

import cats.instances.future._
import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.helptosave.WSHttp
import uk.gov.hmrc.helptosave.connectors.CitizenDetailsConnector.CitizenDetailsResponse
import uk.gov.hmrc.helptosave.models.Address
import uk.gov.hmrc.helptosave.util.{NINO, Result}
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext


/**
  * A connector which connects to the `citizen-details` microservice to obtain
  * a person's address
  */
@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {

  def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse]
}

object CitizenDetailsConnector {

  case class CitizenDetailsPerson(firstName: Option[String],
                                  lastName: Option[String],
                                  dateOfBirth: Option[LocalDate])


  case class CitizenDetailsResponse(person: Option[CitizenDetailsPerson], address: Option[Address])

  implicit val personReads: Reads[CitizenDetailsPerson] = Json.reads[CitizenDetailsPerson]

  implicit val addressReads: Reads[Address] = Json.reads[Address]

  implicit val citizenDetailsResponseReads: Reads[CitizenDetailsResponse] = Json.reads[CitizenDetailsResponse]

}

@Singleton
class CitizenDetailsConnectorImpl extends CitizenDetailsConnector with ServicesConfig {

  private val citizenDetailsBaseURL: String = baseUrl("citizen-details")

  private def citizenDetailsURI(nino: NINO): String = s"$citizenDetailsBaseURL/citizen-details/$nino/designatory-details"

  override def getDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CitizenDetailsResponse] =
    Result(WSHttp.get(citizenDetailsURI(nino))).subflatMap(response ⇒
      if (response.status == 200) {
        response.parseJson[CitizenDetailsResponse]
      } else {
        Left(s"Citizen details response came back with status ${response.status}. Response body was ${response.body}")
      }
    )
}