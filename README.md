help-to-save
============

Backend service for Help to Save.


Table of Contents
=================

* [About Help to Save](#about-help-to-save)
   * [Keywords](#keywords)
   * [Product Background](#product-background)
   * [Product Repos](#product-repos)
   * [Private Beta User Restriction](#private-beta-user-restriction)
* [Running and Testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
* [Endpoints](#endpoints)
* [License](#license)

About Help to Save
========================

Abbreviations
-------------
| Key | Meaning |
|:----------------:|-------------|
|DES| Data Exchange Service (Message Bus) |
|HoD| Head Of Duty, HMRC legacy application |
|HtS| Help To Save |
|MDTP| HMRC Multi-channel Digital Tax Platform |
|NS&I| National Savings & Investments |
|UC| Universal Credit|
|WTC| Working Tax Credit|

Product Background
------------------
The Prime Minister set out the government’s intention to bring forward, a new Help to Save
(‘HtS’) scheme to encourage people on low incomes to build up a “rainy day” fund.

Help to Save will target working families on the lowest incomes to help them build up their
savings. The scheme will be open to 3.5 million adults in receipt of Universal Credit with
minimum weekly household earnings equivalent to 16 hours at the National Living Wage, or those in receipt 
of Working Tax Credit.

A customer can deposit up to a maximum of £50 per month in the account. It will work by
providing a 50% government bonus on the highest amount saved into a HtS account. The
bonus is paid after two years with an option to save for a further two years, meaning that people
can save up to £2,400 and benefit from government bonuses worth up to £1,200. Savers will be
able to use the funds in any way they wish. The published implementation date for this is Q2/2018,
but the project will have a controlled go-live with a pilot population in Q1/2018.

Product Repos
-------------
The suite of repos connected with this Product are as follows:  

| Repo | Description |
|:-----|:------------|
| [help-to-save-frontend](https://github.com/hmrc/help-to-save-frontend)                       | handles requests from browser for public digital journey |
| [help-to-save](https://github.com/hmrc/help-to-save)                                         | handles backend logic for HTS |
| [help-to-save-proxy](https://github.com/hmrc/help-to-save-proxy)                             | handles requests to services outside of MDTP |
| [help-to-save-api](https://github.com/hmrc/help-to-save-api)                                 | handles requests from third parties outside of MDTP |
| [help-to-save-stride-frontend](https://github.com/hmrc/help-to-save-stride-frontend)         | handles requests from browser for internal call centre journey |
| [help-to-save-stub](https://github.com/hmrc/help-to-save-stub)                               | provides endpoints for testing |
| [help-to-save-integration-tests](https://github.com/hmrc/help-to-save-integration-tests)     | contains system integration tests |
| [help-to-save-performance-tests](https://github.com/hmrc/help-to-save-performance-tests)     | contains load performance tests |
| [help-to-save-test-admin-frontend](https://github.com/hmrc/help-to-save-test-admin-frontend) | provides useful functions for testing |

This repo is the JSON create account interface schema between HMRC and NS&I:
- [help-to-save-apis](https://github.com/hmrc/help-to-save-apis/blob/master/1.0/create-account.request.schema.json)

This diagram shows a general picture of how the different services are connected to each other:
```                                                                                                                                                                              
                           browser - internal                    browser - public                                                                                        
                           call centre journey                   digital journey                                                                                         
                                     |                                  |                                                       
                                     |                                  |                                                       
                                     |                                  |                                                       
            +------------------------+----------------------------------+-----------------------+                               
            | MDTP                   |                                  |                       |                               
            |                        |                                  |                       |                               
            |        +---------------+--------------+   +---------------+--------------+        |                               
            |        |                              |   |                              |        |                               
            |        | help-to-save-stride-frontend |   |    help-to-save-frontend     |        |                               
            |        |                              |   |                              |        |                               
            |        +---------------+--------------+   +---------------+--------------+        |                               
            |                        |                                  |                       |                               
            |                        |                                  |                       |                               
            |                        +---------+              +---------+                       |                               
            |                                  |              |                                 |                               
            |                          +-------+--------------+-------+                         |
            |                          |                              |                         |
 HoDs ------+--------------------------+         help-to-save         |                         |
            |                          |                              |                         |
            |                          +-------+--------------+-------+                         |
            |                                  |              |                                 |
            |                       +----------+              +---------+                       |    
            |                       |                                   |                       |
            |                       |                                   |                       |
            |       +---------------+--------------+     +--------------+---------------+       |               
            |       |                              |     |                              |       |               
            |       |       help-to-save-api       |     |      help-to-save-proxy      |       |               
            |       |                              |     |                              |       |               
            |       +---------------+--------------+     +--------------+---------------+       |
            |                       |                                   |                       | 
            +-----------------------+-----------------------------------+-----------------------+                               
                                    |                                   |                                     
                                    |                                   |                                                         
                                    |                                   |                                                         
                                    |                                   |                                                         
                                    |                                   |                                                         
                              incoming requests                requests to third-party                                                               
                              from third-parties                    services                                                                       
```



Private Beta User Restriction
----------------------------

During Private Beta, when a HtS Account is created, per-day-count and total-count counters are incremented. After the customer’s Eligibility
Check, the counters are checked to ensure that the cap’s haven’t been reached. If they have, they are shuttered, otherwise they may continue
to create a HtS account.


Running and Testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7004 by default.

Unit tests
----------
Run `sbt test` on the terminal to run the unit tests.


Endpoints
=========

| Path                         | Method | Description |
|:-----------------------------|:-------|:------------|
| /eligibility-check           | GET  | Checks whether or not a person is eligible for help to save  |
| /enrolment-status            | GET  | Checks whether or not a person is enrolled in help to save |
| /set-itmp-flag               | GET  | Sets the ITMP flag for a user to indicate they have created a HTS account |
| /store-email                 | GET  | Stores the users new email address in persistent storage |
| /get-email                   | GET  | Get the users email from persistent storage |
| /account-create-allowed      | GET  | Checks if user cap has been reached |
| /{NINO}/account              | GET  | Returns account data for the specified NINO, or 404 (NOT FOUND) if there is no account for that NINO |
| /{NINO}/account/transactions | GET  | Returns transaction for the specified NINO, or 404 (NOT FOUND) if there is no account for that NINO |
| /create-account              | POST | Created a HTS account |
| /update-email                | PUT  | Updates email in the HTS account |
| /paye-personal-details       | GET  | Gets user information for HTS |
| /validate-bank-details       | POST | Checks that bank details are valid |

License 
=======
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")


