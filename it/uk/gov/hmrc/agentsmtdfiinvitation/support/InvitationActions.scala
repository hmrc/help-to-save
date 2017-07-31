package uk.gov.hmrc.agentsmtdfiinvitation.support

import org.scalatest.Suite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.mvc.Results

trait InvitationActions extends ActionsSupport {

  this: Suite =>

  def createInvitation ( agentId: String, clientId: String, service: String ): WSResponse = {
    val payload = Json.obj(
      "service" -> service,
      "clientId" -> clientId,
      "invitationReference" → "colm")
    wsClient
      .url(s"$url/agent/$agentId/invitations/sent")
      .post(payload)
      .futureValue
  }

  def deleteInvitation( clientId: String, invitationId: String ): WSResponse =
    wsClient
      .url(s"$url/client/$clientId/invitations/received/$invitationId")
      .delete()
      .futureValue

  def getInvitationsForClient ( clientId: String ): WSResponse =
    wsClient
      .url(s"$url/client/$clientId/invitations/received")
      .get()
      .futureValue

  def getInvitationsForClientByStatus ( clientId: String , status: String ): WSResponse =
    wsClient
      .url(s"$url/client/$clientId/invitations/received?status=$status")
      .get()
      .futureValue

  def getInvitationsFromAgent (agentId: String ): WSResponse =
    wsClient
      .url(s"$url/agent/$agentId/invitations/sent")
      .get()
      .futureValue

  def getInvitationsFromAgentByStatus (agentId: String, status: String ): WSResponse =
    wsClient
      .url(s"$url/agent/$agentId/invitations/sent?status=$status")
      .get()
      .futureValue


  def acceptInvitation (clientId: String, invitationId: String ): WSResponse =
    wsClient
      .url(s"$url/client/$clientId/invitations/received/$invitationId/accept")
      .put(Results.EmptyContent())
      .futureValue


  def rejectInvitation (clientId: String, invitationId: String ): WSResponse =
    wsClient
      .url(s"$url/client/$clientId/invitations/received/$invitationId/reject")
      .put(Results.EmptyContent())
      .futureValue




}
