/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models.enrolment

import org.bson.types.ObjectId
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

case class EnrolmentData(
  nino: String,
  itmpHtSFlag: Boolean,
  eligibilityReason: Option[Int] = None,
  source: Option[String] = None,
  accountNumber: Option[String] = None,
  deleteFlag: Option[Boolean] = None,
  _id: Option[ObjectId] = None
)

object EnrolmentData {
  implicit val objectIdFormat: Format[ObjectId]  = MongoFormats.Implicits.objectIdFormat
  implicit val ninoFormat: Format[EnrolmentData] = Json.format[EnrolmentData]
}
