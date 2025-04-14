package helpers

import org.mongodb.scala.model.Filters
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.result.DeleteResult
import uk.gov.hmrc.helptosave.models.UserCap

trait UserCapStoreRepoHelper {

  self: IntegrationSpecBase =>

  def getUserCap(): Option[UserCap] = await(userCapStoreRepository.get())

  def updateUserCap(): Option[UserCap] = await(
    userCapStoreRepository.upsert(new UserCap(dailyCount = 1, totalCount = 1))
  )

  def deleteUserCap(): Seq[DeleteResult] = await(
    userCapStoreRepository.collection.deleteMany(Filters.empty()).toFuture()
  )

}
