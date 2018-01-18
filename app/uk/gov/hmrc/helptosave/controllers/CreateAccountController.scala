package uk.gov.hmrc.helptosave.controllers

import com.google.inject.Inject
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosave.connectors.FrontendConnector
import uk.gov.hmrc.helptosave.models.{ErrorResponse, NSIUserInfo}
import uk.gov.hmrc.helptosave.util.JsErrorOps._
import uk.gov.hmrc.helptosave.util.{Logging, toFuture}
import uk.gov.hmrc.play.microservice.controller.BaseController

class CreateAccountController @Inject()(frontendConnector: FrontendConnector) extends BaseController with Logging with WithMdcExecutionContext {

  def createAccount(): Action[AnyContent] = Action.async {
    implicit request ⇒ {
      request.body.asJson.map(_.validate[NSIUserInfo]) match {
        case Some(JsSuccess(userInfo, _)) ⇒
          frontendConnector.createAccount(userInfo)
            .map(response ⇒ Status(response.status))

        case Some(error: JsError) ⇒
          val errorString = error.prettyPrint()
          logger.warn(s"Could not parse JSON in request body: $errorString")
          toFuture(BadRequest(ErrorResponse("Could not parse JSON in request", errorString).toJson()))

        case None ⇒
          logger.warn("No JSON body found in request")
          toFuture(BadRequest(ErrorResponse("No JSON found in request body", "").toJson()))
      }
    }
  }
}
