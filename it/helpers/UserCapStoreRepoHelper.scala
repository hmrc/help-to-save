package helpers

import org.mongodb.scala.model.Filters
import uk.gov.hmrc.helptosave.repo.MongoUserCapStore
import uk.gov.hmrc.helptosave.repo.UserCapStore.UserCap

trait UserCapStoreRepoHelper {

  self: IntegrationSpecBase =>

  val userCapStoreRepository: MongoUserCapStore

  def getUserCap(): Option[UserCap] = {
    await(userCapStoreRepository.get())
  }

  def updateUserCap() = await(userCapStoreRepository.upsert(new UserCap(dailyCount = 1, totalCount = 1)))

  def deleteUserCap() = await(userCapStoreRepository.collection.deleteMany(Filters.empty()).toFuture())


}
