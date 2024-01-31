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

import java.nio.charset.Charset
import akka.stream.Materializer
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration, _}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

trait UnitSpec extends AnyWordSpecLike with Matchers {

  implicit val defaultTimeout: FiniteDuration = 50 seconds

  def await[A](future: Future[A])(implicit timeout: Duration): A =
    Await.result(future, timeout)

  def bindModules: Seq[GuiceableModule] = Seq()

  def status(of: play.api.mvc.Result): Int = of.header.status

  def status(of: Future[play.api.mvc.Result])(implicit timeout: Duration): Int =
    status(Await.result(of, timeout))

  def jsonBodyOf(result: play.api.mvc.Result)(
    implicit
    mat: Materializer): JsValue =
    Json.parse(bodyOf(result))

  def jsonBodyOf(resultF: Future[play.api.mvc.Result])(
    implicit
    mat: Materializer): Future[JsValue] =
    resultF.map(jsonBodyOf)

  def bodyOf(result: play.api.mvc.Result)(
    implicit
    mat: Materializer): String = {
    val bodyBytes: ByteString = await(result.body.consumeData)
    // We use the default charset to preserve the behaviour of a previous
    // version of this code, which used new String(Array[Byte]).
    // If the fact that the previous version used the default charset was an
    // accident then it may be better to decode in UTF-8 or the charset
    // specified by the result's headers.
    bodyBytes.decodeString(Charset.defaultCharset().name)
  }

  def bodyOf(resultF: Future[play.api.mvc.Result])(
    implicit
    mat: Materializer): Future[String] =
    resultF.map(bodyOf)

  case class ExternalService(
    serviceName: String,
    runFrom: String = "SNAPSHOT_JAR",
    classifier: Option[String] = None,
    version: Option[String] = None)

}
