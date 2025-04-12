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

package uk.gov.hmrc.helptosave.services

import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.helptosave.audit.HTSAuditor
import uk.gov.hmrc.helptosave.config.AppConfig
import uk.gov.hmrc.helptosave.models.NINODeletionConfig
import uk.gov.hmrc.helptosave.repo.EnrolmentStore
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class ApplicationStart @Inject() (
  appConfig: AppConfig,
  enrolmentStore: EnrolmentStore,
  executionContext: ExecutionContext,
  audit: HTSAuditor
) extends Logging {

  implicit val ec: ExecutionContext = executionContext

  val ninosToDelete: Seq[NINODeletionConfig] = appConfig.ninoDeletionConfig("delete-ninos")
  if (ninosToDelete.nonEmpty) {
    enrolmentStore.updateDeleteFlag(ninosToDelete).value.onComplete {
      case Success(Right(deletedAccounts)) =>
        publishAuditEventForNINOs("AccountsDeleted", deletedAccounts)
        logger.info(s"Successfully deleted list of NINOs: $ninosToDelete")
      case Success(Left(errorMsg))         => logger.error(errorMsg)
      case Failure(ex)                     => logger.error(s"Failed to delete configured list of NINOs: $ninosToDelete", ex)
    }
  }

  val ninosToUndoDeletion: Seq[NINODeletionConfig] = appConfig.ninoDeletionConfig("undo-delete-ninos")
  if (ninosToUndoDeletion.nonEmpty) {
    enrolmentStore.updateDeleteFlag(ninosToUndoDeletion, revertSoftDelete = true).value.onComplete {
      case Success(Right(undeletedAccounts)) =>
        publishAuditEventForNINOs("AccountsUndeleted", undeletedAccounts)
        logger.info(s"Successfully undid deletion of NINOs: $ninosToUndoDeletion")
      case Success(Left(errorMsg))           => logger.error(errorMsg)
      case Failure(ex)                       => logger.error(s"Failed to undo deletion of of NINOs: $ninosToUndoDeletion", ex)
    }
  }

  private def publishAuditEventForNINOs(auditType: String, managedConfigs: Seq[NINODeletionConfig]) = {
    val ninos = managedConfigs
      .map(enrolment =>
        Json.obj(
          "nino"  -> enrolment.nino,
          "docId" -> enrolment.docID.map(_.toHexString)
        )
      )
      .toList
    audit.auditConnector.sendExtendedEvent(
      ExtendedDataEvent(
        appConfig.appName,
        auditType,
        detail = Json.toJson(ninos)
      )
    )
  }
}
