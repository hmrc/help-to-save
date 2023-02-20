import helpers.IntegrationSpecBase
import helpers.WiremockHelper._
import helpers.TestData._
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.AUTHORIZATION

class HelpToSaveControllerISpec extends IntegrationSpecBase {

  override def beforeEach(): Unit = {
    deleteAllEnrolmentData()
    deleteUserCap()
  }

  val urlPath = "/create-account"

  s"POST $urlPath" when {
    "a valid NSIUserInfo request is received from a stride device" that {
      "has not been already created" should {
        "add a record to the enrolments with itmpFlag=true, update userCap and return CREATED" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CREATED, Json.toJson(account).toString())
          stubAudit
          val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true, source = "Stride-Manual"))

          whenReady(res) {resp =>
            resp.status shouldBe CREATED
            getEnrolmentCount(NINO) shouldBe 1
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created and the proxy returns an empty json" should {
        "add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT, Json.obj().toString())
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true, source = "Stride-Manual"))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getEnrolmentCount(NINO) shouldBe 2
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created and the proxy returns an empty body" should {
        "add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT)
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true, source = "Stride-Manual"))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getEnrolmentCount(NINO) shouldBe 2
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }


      "has been already created and the proxy returns an empty string body" should {
        "add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT, "")
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true, source = "Stride-Manual"))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getEnrolmentCount(NINO) shouldBe 2
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }
    }

    "a valid NSIUserInfo request is received from a none stride device" that {
      "has not been already created" should {
        "set the flag on des, add a record to the enrolments with itmpFlag=true, update userCap and return CREATED" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CREATED, Json.toJson(account).toString())
          stubPut(s"/help-to-save/accounts/$NINO", OK)

          stubAudit
          val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CREATED
            eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
              getEnrolmentCount(NINO) shouldBe 1
            }
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has not been already created and des returns 500" should {
        "add a record to the enrolments with itmpFlag=false, update userCap and return CREATED" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CREATED, Json.toJson(account).toString())
          stubPut(s"/help-to-save/accounts/$NINO", INTERNAL_SERVER_ERROR)

          stubAudit
          val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CREATED
            eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
              getEnrolmentCount(NINO, false) shouldBe 1
            }
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created and the proxy returns an empty json" should {
        "set the flag on des, add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT, Json.obj().toString())
          stubPut(s"/help-to-save/accounts/$NINO", OK)
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created, the proxy returns an empty body and des returns 403" should {
        "set the flag on des, add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT)
          stubPut(s"/help-to-save/accounts/$NINO", FORBIDDEN)
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created and the proxy returns an empty string body" should {
        "set the flag on des, add a new record to the enrolments with itmpFlag=true, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT, "")
          stubPut(s"/help-to-save/accounts/$NINO", OK)
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }

      "has been already created and the proxy returns an empty string body and des returns 500" should {
        "set the flag on des, add a new record to the enrolments with itmpFlag=flase, not update userCap and return CONFLICT" in {
          stubPost("/auth/authorise", OK, Json.obj().toString())
          stubPost("/help-to-save-proxy/create-account", CONFLICT, "")
          stubPut(s"/help-to-save/accounts/$NINO", INTERNAL_SERVER_ERROR)
          stubAudit
          insertEnrolmentData(NINO, true, Some(7), "Digital", Some("AC01"))
          updateUserCap()
          lazy val res = buildRequest(urlPath)
            .addHttpHeaders("X-Request-Id" -> "one-two-three", AUTHORIZATION -> "Bearer some-token")
            .post(validCreateAccountRequestPayload(true))

          whenReady(res) { resp =>
            resp.status shouldBe CONFLICT
            getUserCap().map(_.totalCount) shouldBe Some(1)
          }
        }
      }
    }
  }

}
