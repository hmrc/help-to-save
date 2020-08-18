package uk.gov.hmrc.helptosave.util

import play.api.libs.json._

object JsLookupHelper{

  /**
   * Return the property corresponding to the fieldName, supposing we have a JsObject.
   *
   * @param fieldName the name of the property to look up
   */
  def lookup(fieldName: String, value: JsValue): JsLookupResult  = JsDefined(value) match {
    case JsDefined(obj @ JsObject(_)) =>
      obj.value.get(fieldName).map(JsDefined.apply)
        .getOrElse(JsUndefined(s"'$fieldName' is undefined on object: ${obj.keys}"))
    case JsDefined(o) =>
      JsUndefined(s"submitted json is not an object")
    case undef => undef
  }

  /**
   * Access a value of this array.
   *
   * @param index Element index
   */
  def lookup(index: Int , value: JsValue): JsLookupResult = JsDefined(value) match {
    case JsDefined(arr: JsArray) =>
      arr.value.lift(index).map(JsDefined.apply).getOrElse(JsUndefined(s"Array index out of bounds in $arr"))
    case JsDefined(o) =>
      JsUndefined(s"$o is not an array")
    case undef => undef
  }

}
