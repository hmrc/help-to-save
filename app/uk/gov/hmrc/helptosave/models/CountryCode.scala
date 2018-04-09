/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.libs.json._

import scala.collection.Map
import scala.io.Source

object CountryCode {

  val countryCodes: Map[Int, String] = {
    val content = Source.fromInputStream(getClass.getResourceAsStream("/resources/country.json")).mkString
    Json.parse(content) match {
      case JsObject(fields) ⇒
        fields
          .map(x ⇒ (x._1.toInt, (x._2 \ "alpha_two_code").asOpt[String]))
          .collect { case (id, Some(value)) ⇒ id -> value }
      case _ ⇒ sys.error("no country codes were found, terminating the service")
    }
  }
}
