package helpers

import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.result.DeleteResult
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

trait EnrolmentStoreRepoHelper {

  self: IntegrationSpecBase =>

  def getEnrolmentCount(nino: String, itmpFlag: Boolean = true): Long =
    await(
      enrolmentStoreRepository.collection
        .countDocuments(and(Filters.equal("nino", nino), Filters.equal("itmpHtSFlag", itmpFlag)))
        .toFuture()
    )

  def insertEnrolmentData(
    nino: NINO,
    itmpFlag: Boolean,
    eligibilityReason: Option[Int],
    source: String,
    accountNumber: Option[String],
    deleteFlag: Option[Boolean] = None
  )(implicit hc: HeaderCarrier): Either[NINO, Unit] =
    await(enrolmentStoreRepository.insert(nino, itmpFlag, eligibilityReason, source, accountNumber, deleteFlag).value)

  def deleteAllEnrolmentData(): DeleteResult = await(
    enrolmentStoreRepository.collection.deleteMany(Filters.empty()).toFuture()
  )

}
