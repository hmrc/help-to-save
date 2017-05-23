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