package helpers

import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters._
import uk.gov.hmrc.helptosave.repo.MongoEnrolmentStore
import uk.gov.hmrc.helptosave.util.NINO
import uk.gov.hmrc.http.HeaderCarrier

trait EnrolmentStoreRepoHelper {

  self: IntegrationSpecBase =>

  val enrolmentStoreRepository: MongoEnrolmentStore

  def getEnrolmentCount(nino: String, itmpFlag: Boolean = true): Long = {
    await(enrolmentStoreRepository.collection.countDocuments(and(Filters.equal("nino", nino), Filters.equal("itmpHtSFlag", itmpFlag))).toFuture())
  }

  def insertEnrolmentData(nino: NINO,
                          itmpFlag: Boolean,
                          eligibilityReason: Option[Int],
                          source: String,
                          accountNumber: Option[String],
                          deleteFlag: Option[Boolean] = None)(implicit hc: HeaderCarrier) = {
    await(enrolmentStoreRepository.insert(nino, itmpFlag, eligibilityReason, source, accountNumber, deleteFlag).value)
  }

  def deleteAllEnrolmentData() = await(enrolmentStoreRepository.collection.deleteMany(Filters.empty()).toFuture())

}
