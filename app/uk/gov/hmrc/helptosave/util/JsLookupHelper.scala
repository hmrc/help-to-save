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

package uk.gov.hmrc.helptosave.util

import play.api.libs.json._

object JsLookupHelper {

  /**
   * Return the property corresponding to the fieldName, supposing we have a JsObject.
   *
   * @param fieldName the name of the property to look up
   */
  def lookup(fieldName: String, value: JsValue): JsLookupResult = JsDefined(value) match {
    case JsDefined(obj @ JsObject(_)) =>
      obj.value.get(fieldName).map(JsDefined.apply)
        .getOrElse(JsUndefined(s"'$fieldName' is undefined on object: ${obj.keys.mkString(",")}"))
    case JsDefined(o) =>
      JsUndefined("submitted json is not an object")
    case undef => undef
  }

  /**
   * Access a value of this array.
   *
   * @param index Element index
   */
  def lookup(index: Int, value: JsValue): JsLookupResult = JsDefined(value) match {
    case JsDefined(arr: JsArray) =>
      arr.value.lift(index).map(JsDefined.apply).getOrElse(JsUndefined(s"Array index out of bounds in $arr"))
    case JsDefined(o) =>
      JsUndefined(s"$o is not an array")
    case undef => undef
  }

}
