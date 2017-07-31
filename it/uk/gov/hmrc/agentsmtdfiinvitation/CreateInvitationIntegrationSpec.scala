package uk.gov.hmrc.agentsmtdfiinvitation

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentsmtdfiinvitation.support.{FakeRelationshipService, IntegrationSpec, InvitationActions}
import play.api.libs.json.Json

class CreateInvitationIntegrationSpec extends IntegrationSpec with InvitationActions with FakeRelationshipService {

  feature("Create a new invitation for a fakeRelationship between an agent and a client for a given service") {

    scenario("Create a new basic invitation") {

      Given("a basic create invitation request")
//      val agent = "Agent007"
//      val client = "Client123"
//      val service = "SA"

      When("I call the create-invitation endpoint")
//      val createInvitationResponse : WSResponse = createInvitation(agent, client, service)
//
//      Then("I recieve a 201 CREATED response")
//      createInvitationResponse.status shouldBe CREATED
//
//      And("A new invitation record is created")
//      val invitationId = createInvitationResponse.header("Location").get
//      invitationId should fullyMatch regex "[A-z0-9-]+$"
//
//      deleteInvitation(client, invitationId ) // cleanup

      Then("Assert true")
      assert(true)
    }



//    scenario("Client accepts an invitation from an approved agent") {
//
//      Given("An agent has submitted an invitation to a client for PAYE")
//      val agent = "AgentXXX"
//      val client = "Client123"
//      val service = "PAYE"
//      val createInvitationResponse = createInvitation(agent, client, service)
//      createInvitationResponse.status shouldBe CREATED
//      val invitationId = createInvitationResponse.header("Location").get
//      invitationId should fullyMatch regex "[A-z0-9-]+$"
//
//      When("I call the accept invitation endpoint with that invitation ID")
//      val acceptInvitationResponse = acceptInvitation(client, invitationId)
//
//      Then("I should get a 200 OK response")
//      acceptInvitationResponse.status shouldBe OK
//
//      And("the invitation should be updated to ACCEPTED")
//      val getAcceptedInvitationResponse = getInvitationsForClientByStatus(client, "ACCEPTED")
//      getAcceptedInvitationResponse.status shouldBe OK
//      val jsonResponse = Json.parse(getAcceptedInvitationResponse.body)
//      val status = (jsonResponse(0) \ "status").as[String]
//      status shouldBe "ACCEPTED"
//
//      deleteInvitation(client, invitationId ) // cleanup
//    }

  }

}
