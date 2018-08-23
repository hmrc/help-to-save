/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.helptosave.repo

import java.net.ServerSocket

import com.github.simplyscala.{MongoEmbedDatabase, MongodProps}
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.FailoverStrategy
import reactivemongo.core.actors.Exceptions.PrimaryUnavailableException
import uk.gov.hmrc.mongo.MongoConnector

trait MongoSupport extends MongoEmbedDatabase { this: Eventually with Matchers ⇒

  def withMongo(f: ReactiveMongoComponent ⇒ Any): Unit = withMongo(mongo(_, None), f)

  def withBrokenMongo(f: ReactiveMongoComponent ⇒ Any): Unit =
    scala.util.control.Exception.ignoring(classOf[PrimaryUnavailableException]) {
      withMongo(brokenMongo, f)
    }

  private def withMongo(newReactiveMongoComponent: MongodProps ⇒ ReactiveMongoComponent, f: ReactiveMongoComponent ⇒ Any): Unit = {
    val port = {
      val socket = new ServerSocket(0)
      socket.close()

      eventually {
        socket.isClosed shouldBe true
      }

      socket.getLocalPort
    }

    withEmbedMongoFixture(port) { mongodProps ⇒
      val component = newReactiveMongoComponent(mongodProps)

      try {
        f(component)
      } finally {
        component.mongoConnector.close()
      }
    }
  }

  override def mongoStop(mongodProps: MongodProps): Unit = {
    super.mongoStop(mongodProps)
    eventually{
      mongodProps.mongodProcess.isProcessRunning shouldBe false
    }
  }

  private def mongo(mongodProps: MongodProps, failoverStrategy: Option[FailoverStrategy] = None): ReactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = {
      val address = mongodProps.mongodProcess.getConfig.net()
      MongoConnector(
        s"mongodb://${address.getServerAddress.getHostAddress}:${address.getPort}/help-to-save",
        failoverStrategy
      )
    }
  }

  private def brokenMongo(mongodProps: MongodProps): ReactiveMongoComponent = {
    val component = mongo(mongodProps, Some(FailoverStrategy(retries = 0)))

    eventually {
      component.mongoConnector.helper.connection.active shouldBe true
    }

    mongodProps.mongodProcess.stop()

    eventually{
      mongodProps.mongodProcess.isProcessRunning shouldBe false
    }

    component

  }

}
