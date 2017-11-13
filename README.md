# help-to-save 

[![Build Status](https://travis-ci.org/hmrc/help-to-save.svg)](https://travis-ci.org/hmrc/help-to-save) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save/_latestVersion)


The `help-to-save` service will run on port 7001 when started by the service manager.

## Endpoints

# GET /eligibility-check
 Checks whether or not a person is eligible for help to save. This endpoint requires two query parameters:
 
 | parameter      | description                                      |
 |----------------|--------------------------------------------------|
 | nino           | The NINO of the applicant                        |
 | userDetailsURI | The URI which can be used to obtain user details |
 
 For example:
 ```
 /eligibility-check?nino=NINO&userDetailsURI=http%3A%2F%2Fuser-details%2Fid%2F2390uj%23few3r%2B
 ```
 If eligible, expect a `200` response with JSON describing the user. If not eligible expect a `200`
response with an empty body.

# GET /enrolment-status
 Checks whether or not a person is enrolled in help to save. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |

  For example:
   ```
   /enrolment-status?nino=NINO&userDetailsURI=http%3A%2F%2F
   ```
   If enrolled, expect a `200` response with JSON describing the user. If not eligible expect a `200`
  response with an empty body.

# GET /enrol-user
 Enrols user into help to save. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |

  For example:
   ```
   /enrol-user?nino=NINO&userDetailsURI=http%3A%2F%2F
   ```
   If eligible, expect a `200` response. If not eligible expect a `400` response and returns the nino.

# GET /set-itmp-flag
 Sets the ITMP flag when a user has successfully enrolled into help to save. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |

  For example:
   ```
   /set-itmp-flag?nino=NINO&userDetailsURI=http%3A%2F%2F
   ```
   If successful, expect a `200` response. If setting flag is unsuccessful expect a `500` response.

# GET /store-email
 Stores the users new email address in help to save mongo collection. This endpoint requires two parameters:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |
  | email          | The user's new email address                     |

  For example:
   ```
   /store-email?nino=NINO&userDetailsURI=http%3A%2F%2F&email=gfgfds%sd32%45
   ```
   If successful, expect a `200` response. If storing email is unsuccessful expect a `500` response.

# GET /get-email
 Gets the stored users email. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |

  For example:
   ```
   /get-email?nino=NINO&userDetailsURI=http%3A%2F%2F
   ```
   If successful, expect a `200` response with JSON containing the user's email address. If unsuccessful expect a `500` response.

# GET /account-create-allowed
 Checks if user cap has been reached. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | nino           | The NINO of the applicant                        |

  For example:
   ```
   /account-create-allowed?nino=NINO&userDetailsURI=http%3A%2F%2F
   ```
   Expect a `200` response with JSON containing the boolean result.

# POST /update-user-count
 Updates the number of users who have enrolled into help to save. Expect a `200` response.
 
# POST /create-an-account
Creates a help to save account. Requires the user information in the request body as JSON. This
should be the same JSON passed back from the `GET /eligibility-check` endpoint. Expect the 
following responses:

| status | description                                                                                                         |
|--------|---------------------------------------------------------------------------------------------------------------------|
| 201    | The account was successfully created                                                                                |
| 401    | No JSON was found in the request, the JSON was invalid or the user information did not pass NS&I validation checks  |
| 500    | Account creation failed with NS&I    


### License 

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
