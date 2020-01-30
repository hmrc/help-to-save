/*
 * Copyright 2020 HM Revenue & Customs
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

import scala.collection.Map
import scala.io.Source

object CallingCodes {

  val callingCodes: Map[Int, String] = {
    Source.fromInputStream(getClass.getResourceAsStream("/resources/callingcodes.txt"))
      .getLines()
      .foldLeft(Map.empty[Int, String]) {
        case (acc, curr) ⇒
          curr.split("-").toList match {
            case key :: value :: Nil ⇒ acc.updated(key.trim.toInt, value.trim)
            case _                   ⇒ acc
          }
      }
  }
}
