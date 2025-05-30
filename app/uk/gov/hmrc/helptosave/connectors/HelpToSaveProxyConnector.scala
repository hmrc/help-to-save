/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosave.connectors

import cats.data.{EitherT, NonEmptyList}
import cats.instances.int._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.http.Status.{BAD_REQUEST, CONFLICT}
import play.mvc.Http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.metrics.Metrics
import uk.gov.hmrc.helptosave.models._
import uk.gov.hmrc.helptosave.models.account.GetAccountErrorResponse
import uk.gov.hmrc.helptosave.models.account.{Account, NsiAccount, NsiTransactions, Transactions}
import uk.gov.hmrc.helptosave.util.HeaderCarrierOps._
import uk.gov.hmrc.helptosave.util.HttpResponseOps._
import uk.gov.hmrc.helptosave.util.Logging._
import uk.gov.hmrc.helptosave.util.{LogMessageTransformer, Logging, PagerDutyAlerting, Result}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.libs.ws.writeableOf_JsValue

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveProxyConnectorImpl])
trait HelpToSaveProxyConnector {

  def createAccount(
    userInfo: NSIPayload
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]]

  def updateEmail(
    userInfo: NSIPayload
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]]

  def ucClaimantCheck(nino: String, txnId: UUID, threshold: Double)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[UCResponse]

  /** If NS&I do not recognise the given NINO return None
    */
  def getAccount(nino: String, systemId: String, correlationId: String, path: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[Option[Account]]

  def getTransactions(nino: String, systemId: String, correlationId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[Option[Transactions]]
}

@Singleton
class HelpToSaveProxyConnectorImpl @Inject() (
  http: HttpClientV2,
  metrics: Metrics,
  pagerDutyAlerting: PagerDutyAlerting,
  auditor: HTSAuditor,
  servicesConfig: ServicesConfig
)(implicit transformer: LogMessageTransformer, appConfig: AppConfig)
    extends HelpToSaveProxyConnector
    with Logging {

  val proxyURL: String = servicesConfig.baseUrl("help-to-save-proxy")

  val getAccountVersion: String      = appConfig.runModeConfiguration.underlying.getString("nsi.get-account.version")
  val getTransactionsVersion: String =
    appConfig.runModeConfiguration.underlying.getString("nsi.get-transactions.version")

  val noAccountErrorCode: String =
    appConfig.runModeConfiguration.underlying.getString("nsi.no-account-error-message-id")

  override def createAccount(
    userInfo: NSIPayload
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]] =
    http
      .post(url"$proxyURL/help-to-save-proxy/create-account")
      .withBody(Json.toJson(userInfo))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map { response =>
        response.left.map { upstreamErrorResponse =>
          logger.warn(
            s"unexpected error from proxy during /create-de-account, message=${upstreamErrorResponse.getMessage}",
            userInfo.nino,
            "apiCorrelationId" -> getApiCorrelationId()
          )
          upstreamErrorResponse.statusCode match {
            case 409 => UpstreamErrorResponse("Upstream Error", CONFLICT)
            case 400 => UpstreamErrorResponse("Upstream Error", BAD_REQUEST)
          }
        }
      }
      .recover { case e =>
        val error = s"unexpected error from proxy during /create-de-account ${e.getMessage}"
        Left(UpstreamErrorResponse(error, INTERNAL_SERVER_ERROR))
      }

  override def updateEmail(
    userInfo: NSIPayload
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, HttpResponse]] =
    http
      .put(url"$proxyURL/help-to-save-proxy/update-email")
      .withBody(Json.toJson(userInfo))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map { error =>
        error.left.flatMap { upstreamErrorResponse =>
          logger.warn(
            s"unexpected error from proxy during /update-email, message=${upstreamErrorResponse.getMessage}",
            userInfo.nino,
            "apiCorrelationId" -> getApiCorrelationId()
          )
          val errorJson =
            ErrorResponse("unexpected error from proxy during /update-email", s"${upstreamErrorResponse.getMessage}")
              .toJson()
          Left(UpstreamErrorResponse(errorJson.toString, INTERNAL_SERVER_ERROR))
        }
      }

  override def ucClaimantCheck(nino: String, txnId: UUID, threshold: Double)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[UCResponse] = {
    val url = url"$proxyURL/help-to-save-proxy/uc-claimant-check"

    EitherT[Future, String, UCResponse](
      http
        .get(url)
        .transform(
          _.withQueryStringParameters(
            "nino"          -> nino,
            "transactionId" -> txnId.toString,
            "threshold"     -> threshold.toString
          )
        )
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
        .map { maybeResponse =>
          val correlationId = "apiCorrelationId" -> getApiCorrelationId()

          maybeResponse
            .map { response =>
              val result = response.parseJson[UCResponse]
              result.fold(
                e =>
                  logger.warn(
                    s"Could not parse UniversalCredit response, received 200 (OK), error=$e",
                    nino,
                    correlationId
                  ),
                _ =>
                  logger
                    .info(s"Call to check UniversalCredit check is successful, received 200 (OK)", nino, correlationId)
              )
              result

            }
            .left
            .flatMap { error =>
              logger.warn(
                s"Call to check UniversalCredit check unsuccessful. Received unexpected status ${error.statusCode}",
                nino,
                correlationId
              )
              Left(s"Received unexpected status(${error.statusCode}) from UniversalCredit check")
            }
            .flatten
        }
    )
  }

  override def getAccount(nino: String, systemId: String, correlationId: String, path: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[Option[Account]] = {

    val url          = url"$proxyURL/help-to-save-proxy/nsi-services/account"
    val timerContext = metrics.getAccountTimer.time()
    EitherT[Future, String, Option[Account]](
      http
        .get(url)
        .transform(
          _.withQueryStringParameters(
            "nino"          -> nino,
            "correlationId" -> correlationId,
            "version"       -> getAccountVersion,
            "systemId"      -> systemId
          )
        )
        .execute[HttpResponse]
        .map[Either[String, Option[Account]]] { response =>
          val _ = timerContext.stop()
          response.status match {
            case Status.OK =>
              auditor.sendEvent(GetAccountResultEvent(GetAccountResult(nino, response.json), path), nino)

              val result = for
                nsiAccount <- response.parseJsonWithoutLoggingBody[NsiAccount].leftMap(NonEmptyList.one)
                account    <- Account(nsiAccount).toEither
              yield account

              result.bimap(
                { errors =>
                  metrics.getAccountErrorCounter.inc()
                  pagerDutyAlerting.alert("Could not parse JSON in the getAccount response")
                  logger.debug(s"Response body that failed to parse: ${response.body}}")
                  s"Could not parse getNsiAccount response, received 200 (OK), error=[${errors.toList.mkString(",")}]"
                },
                { account =>
                  logger.info("Call to getNsiAccount successful", nino, "correlationId" -> correlationId)
                  Some(account)
                }
              )

            case other =>
              if isAccountDoesntExistResponse(response) then {
                logger.info("Account didn't exist for getNsiAccount request", nino, "correlationId" -> correlationId)
                Right(None)
              } else {
                logger.warn(
                  s"Call to getNsiAccount unsuccessful. Received unexpected status $other. Body was ${response.body}",
                  nino,
                  "correlationId" -> correlationId
                )
                metrics.getAccountErrorCounter.inc()
                pagerDutyAlerting.alert("Received unexpected http status in response to getAccount")

                Left(s"Received unexpected status($other) from getNsiAccount call")
              }

          }
        }
        .recover { case NonFatal(e) =>
          val _ = timerContext.stop()
          metrics.getAccountErrorCounter.inc()
          pagerDutyAlerting.alert("Failed to make call to getAccount")
          Left(s"Call to getNsiAccount unsuccessful: ${e.getMessage}")
        }
    )
  }

  override def getTransactions(nino: String, systemId: String, correlationId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Result[Option[Transactions]] = {

    val url          = url"$proxyURL/help-to-save-proxy/nsi-services/transactions"
    val timerContext = metrics.getTransactionsTimer.time()
    EitherT[Future, String, Option[Transactions]](
      http
        .get(url)
        .transform(
          _.withQueryStringParameters(
            "nino"          -> nino,
            "correlationId" -> correlationId,
            "version"       -> getAccountVersion,
            "systemId"      -> systemId
          )
        )
        .execute[HttpResponse]
        .map[Either[String, Option[Transactions]]] { response =>
          val _ = timerContext.stop()
          response.status match {
            case Status.OK =>
              val result = for
                nsiTransactions <- response.parseJsonWithoutLoggingBody[NsiTransactions].leftMap(NonEmptyList.one)
                transactions    <- Transactions(nsiTransactions).toEither
              yield transactions

              result.bimap(
                { errors =>
                  metrics.getTransactionsErrorCounter.inc()
                  pagerDutyAlerting.alert("Could not parse get transactions response")
                  logger.debug(s"Response body that failed to parse: ${response.body}}")
                  s"""Could not parse transactions response from NS&I, received 200 (OK), error=[${errors.toList
                      .mkString(",")}]"""
                },
                { (transactions: Transactions) =>
                  logger.info("Call to get transactions successful", nino, "correlationId" -> correlationId)
                  Some(transactions)
                }
              )

            case other =>
              if isAccountDoesntExistResponse(response) then {
                logger.info("Account didn't exist for get transactions request", nino, "correlationId" -> correlationId)
                Right(None)
              } else {
                logger.warn(
                  s"Call to get transactions unsuccessful. Received unexpected status $other. Body was ${response.body}",
                  nino,
                  "correlationId" -> correlationId
                )
                metrics.getTransactionsErrorCounter.inc()
                pagerDutyAlerting.alert("Received unexpected http status in response to get transactions")

                Left(s"Received unexpected status($other) from get transactions call")
              }
          }
        }
        .recover { case NonFatal(e) =>
          val _ = timerContext.stop()
          metrics.getTransactionsErrorCounter.inc()
          logger.warn("Failed to make call to get transactions", e)
          pagerDutyAlerting.alert("Failed to make call to get transactions")
          Left(s"Call to get transactions unsuccessful: ${e.getMessage}")
        }
    )
  }

  private def isAccountDoesntExistResponse(response: HttpResponse): Boolean =
    response.status === Status.BAD_REQUEST &&
      response
        .parseJson[GetAccountErrorResponse]
        .toOption
        .exists(_.errors.exists(_.errorMessageId === noAccountErrorCode))
}
