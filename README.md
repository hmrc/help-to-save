[![Build Status](https://travis-ci.org/hmrc/help-to-save.svg)](https://travis-ci.org/hmrc/help-to-save) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save/_latestVersion)
  
## Help to Save

Backend application process for Help to Save.

## About Help to Save

Please click [here](https://github.com/hmrc/help-to-save-frontend#product-repos) for more information

## How to run

The `help-to-save` service will run on port 7001 when started by the service manager.

Start service manager with the following command to run the service with all required dependencies:
```
sm --start HTS_ALL -f
```

## How to test

The unit tests can be run by running
```
sbt test
```

## How to deploy

This microservice is deployed as per all MDTP microservices via Jenkins into a Docker slug running on a Cloud Provider.

## Endpoints

# GET /eligibility-check
 Checks whether or not a person is eligible for help to save. This endpoint requires no parameters.
 
 For example:
 ```
 /eligibility-check
 ```
 If the call is successful, expect a `200` response with JSON containing the eligibility result. If call is not successful expect a `500`
 response.

# GET /:nino/account
 Returns account data for the specified NINO (National Insurance Number), or 404 if there is no account for that NINO.
 
# GET /:nino/account/transactions
 Returns transaction for the specified NINO (National Insurance Number), or 404 if there is no account for that NINO.
 
  The JSON format for successful `200` responses is:

```json  
{
  "transactions": [
    {
      "amount": 3.00,
      "operation": "credit",
      "transactionDate": "2018-02-18",
      "accountingDate": "2018-02-18",
      "description": "Debit card online deposit",
      "transactionReference": "A1A11AA1A00A0034",
      "balanceAfter": 3.00
    },
    {
      "amount": 6.67,
      "operation": "debit",
      "transactionDate": "2018-02-20",
      "accountingDate": "2018-02-22",
      "description": "BACS payment",
      "transactionReference": "A1A11AA1A00A000I",
      "balanceAfter": 9.67
    },
    {
      "amount": 5.00,
      "operation": "debit",
      "transactionDate": "2018-02-25",
      "accountingDate": "2018-02-25",
      "description": "BACS payment",
      "transactionReference": "A1A11AA1A00A000G",
      "balanceAfter": 4.67
    }
  ]
}
```

# GET /enrolment-status
 Checks whether or not a person is enrolled in help to save. This endpoint requires no parameters.

  For example:
   ```
   /enrolment-status
   ```
  If the call is successful, expect a `200` response with JSON containing the enrolment status. If not successful expect a `500`
  response.

# GET /enrol-user
 Enrols user into help to save. This endpoint requires no parameters.

  For example:
   ```
   /enrol-user
   ```
  If enrolment was successful, expect a `200` response. If not successful expect a `500` response.

# GET /set-itmp-flag
 Sets the ITMP flag when a user has successfully enrolled into help to save. This endpoint requires no parameters.

  For example:
   ```
   /set-itmp-flag
   ```
   If successful, expect a `200` response. If setting flag is unsuccessful expect a `500` response.

# GET /store-email
 Stores the users new email address in help to save mongo collection. This endpoint requires one parameter:

  | parameter      | description                                      |
  |----------------|--------------------------------------------------|
  | email          | The user's new email address                     |

  For example:
   ```
   /store-email?email=gfgfds%sd32%45
   ```
   If successful, expect a `200` response. If storing email is unsuccessful expect a `500` response.

# GET /get-email
 Gets the stored users email. This endpoint requires no parameters.

  For example:
   ```
   /get-email
   ```
   If successful, expect a `200` response with JSON containing the user's email address. If unsuccessful expect a `500` response.

# GET /account-create-allowed
 Checks if user cap has been reached. This endpoint requires no parameters.

  For example:
   ```
   /account-create-allowed
   ```
   Expect a `200` response with JSON containing a boolean result.

# POST /update-user-count
 Updates the number of users who have enrolled into help to save. This endpoint requires no parameters.
 Expect a `200` response.


### License 

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")


