/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.Base64

import com.typesafe.config.ConfigFactory
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.util.{Random, Success}

class CryptoImplSpec extends TestSupport with ScalaCheckDrivenPropertyChecks {

  "The CryptoImpl" must {

    val key = {
      // create a 256 bit key
      val bytes = new Array[Byte](32)
      Random.nextBytes(bytes)
      new String(Base64.getEncoder.encode(bytes))
    }

    val encrypter = new CryptoImpl(Configuration(ConfigFactory.parseString(
      s"""
        | crypto.encryption-key = "$key"
      """.stripMargin)))

    "correctly encrypt and decrypt the data given" in {

      val original = "user+mailbox/department=shipping@example.com"

      val encoded = encrypter.encrypt(original)

      encoded should not be original

      val decoded = encrypter.decrypt(encoded)

      decoded should be(Success(original))
    }

    "correctly encrypt and decrypt the data when there are special characters" in {

      val original = "Dörte@Sören!#$%&'*+-/=?^_`उपयोगकर्ता@उदाहरण.कॉम.{|}~@example.com"

      val encoded = encrypter.encrypt(original)

      encoded should not be original

      val decoded = encrypter.decrypt(encoded)

      decoded should be(Success(original))
    }

    "return an error when there are errors decrypting" in {
      forAll{ s: String ⇒
        whenever(s.nonEmpty){
          encrypter.decrypt(s).isFailure shouldBe true
        }
      }
    }

  }
}
