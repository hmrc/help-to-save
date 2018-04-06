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

import cats.instances.int._
import cats.syntax.eq._

import scala.collection.Map
import scala.io.Source

object CallingCodes {

  private val callingCodes: Map[Int, String] = {
    var codes = Map[Int, String]()
    val content = Source.fromInputStream(getClass.getResourceAsStream("/resources/callingcodes.txt")).getLines()
    content.foreach {
      row ⇒
        val arr = row.split("-")
        if (arr.size === 2) {
          codes.+=(arr(0).trim.toInt -> arr(1).trim)
        }
    }
    codes
  }

  def getCodeFor(id: Int): Option[String] =
    callingCodes.get(id)
}
