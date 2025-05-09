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

import uk.gov.hmrc.helptosave.utils.TestSupport

import scala.util.{Failure, Success, Try}

class TryOpsSpec extends TestSupport {

  "TryOps" must {

    "provide a fold method" in {
      def test(f: Try[Int]): Boolean = f.fold(_ => false, _ => true)

      test(Success(1))               shouldBe true
      test(Failure(new Exception())) shouldBe false
    }

  }

}
