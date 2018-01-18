package uk.gov.hmrc.helptosave.models

import play.api.libs.json.{Format, JsValue, Json}

case class ErrorResponse(errorMessageId: String, errorMessage: String, errorDetails: String)

object ErrorResponse {

  def apply(errorMessage: String, errorDetails: String): ErrorResponse =
    ErrorResponse("", errorMessage, errorDetails)

  implicit val format: Format[ErrorResponse] = Json.format[ErrorResponse]

  implicit class ErrorResponseOps(val errorResponse: ErrorResponse) extends AnyVal {
    def toJson(): JsValue = format.writes(errorResponse)
  }

}
