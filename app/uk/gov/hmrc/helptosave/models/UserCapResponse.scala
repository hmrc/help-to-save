package uk.gov.hmrc.helptosave.models

case class UserCapResponse(isDailyCapReached: Boolean = false, isTotalCapReached: Boolean = false, forceDisabled: Boolean = false )

