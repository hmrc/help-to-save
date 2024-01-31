/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.models

import org.bson.types.ObjectId
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits.objectIdFormat

case class NINODeletionConfig(nino: NINO, docID: Option[ObjectId] = None)

object NINODeletionConfig {
  implicit val format: Format[NINODeletionConfig] = {
    ((__ \ "nino").format[String] and (__ \ "docID").formatNullable[ObjectId])(
      NINODeletionConfig.apply,
      deletionConfig => (deletionConfig.nino, deletionConfig.docID)
    )
  }
}
