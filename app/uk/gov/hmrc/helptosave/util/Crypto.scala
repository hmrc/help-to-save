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

package uk.gov.hmrc.helptosave.util

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.crypto._

import scala.util.Try

@ImplementedBy(classOf[CryptoImpl])
trait Crypto {

  def encrypt(s: String): String

  def decrypt(s: String): Try[String]
}

@Singleton
class CryptoImpl @Inject()(configuration: Configuration) extends AesGCMCrypto with Crypto {

  val encryptionKey: String = configuration.underlying.getString("crypto.encryption-key")

  def encrypt(s: String): String = encrypt(PlainText(s)).value

  def decrypt(s: String): Try[String] = Try(decrypt(Crypted(s)).value)

}
