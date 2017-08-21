/*
 * Copyright 2017 HM Revenue & Customs
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

import com.typesafe.config.ConfigFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.Configuration
import uk.gov.hmrc.helptosave.utils.TestSupport

class DataEncrypterSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  "The DataEncrypter" must {

    val encrypter = new DataEncrypterImpl(Configuration(ConfigFactory.parseString(
      """
        |data-encrypter.seed = "test-seed"
      """.stripMargin)))

    "correctly encrypt and decrypt the data given" in {

      val original = "user+mailbox/department=shipping@example.com"

      val encoded = encrypter.encrypt(original)

      encoded should not be original

      val decoded = encrypter.decrypt(encoded)

      decoded should be(Right(original))
    }

    "correctly encrypt and decrypt the data when there are special characters" in {

      val original = "Dörte@Sören!#$%&'*+-/=?^_`उपयोगकर्ता@उदाहरण.कॉम.{|}~@example.com"

      val encoded = encrypter.encrypt(original)

      encoded should not be original

      val decoded = encrypter.decrypt(encoded)

      decoded should be(Right(original))
    }

    "return an error when there are errors decrypting" in {
      forAll{ s: String ⇒
        whenever(s.nonEmpty){
          encrypter.decrypt(s).isLeft shouldBe true
        }
      }
    }

  }
}
