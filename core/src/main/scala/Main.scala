/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.reebo.ethsync.core

import java.nio.ByteBuffer

import com.reebo.ethsync.core.Protocol.{FullBlock, FullTX, ShallowTX}
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.typesafe.scalalogging.LazyLogging
import monix.eval.{MVar, Task}
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.language.implicitConversions

case class Config(network: String,
                  nodes: Seq[String],
                  kafka: Seq[String],
                  topic: String)

object Main extends App with LazyLogging {
  val mainScheduler = Scheduler.io(s"main")
  val retryPersistence = BackoffRetry(10, 1.seconds)
  val retriever = new BlockRetriever {
    override def getBlock(height: Long): Task[Protocol.FullBlock[ShallowTX]] = ???
  }

  val networks = Seq("mainnet", "kovan", "rinkeby", "ropsten")
    .filter { n =>
      sys.env.isDefinedAt(s"${n.toUpperCase()}_NODES") &&
        sys.env.isDefinedAt(s"${n.toUpperCase()}_TOPIC")
    }

  val configs = networks
    .map { n =>
      (n,
        getStrings(n, "NODES"),
        getStrings(n, "BROKERS"),
        getString(n, "TOPIC"))
    }
    .map { case (n, nodes, brokers, topic) =>
      Config(n, nodes, brokers, topic)
    }

  logger.info(configs.toString())

  Task
    .gatherUnordered(configs.map { c => setup(c).executeAsync })
    .onErrorHandle { err =>
      logger.error(err.getMessage)
    }
    .runOnComplete { _ =>
      logger.info("Shutdown.")
    }(mainScheduler)


  def getStrings(name: String, suffix: String) = {
    sys.env.getOrElse(s"${name.toUpperCase()}_$suffix",
      throw new Exception(
        s"${name}_$suffix not found")
    ).split(",")
  }

  def getString(name: String, suffix: String) = {
    sys.env.getOrElse(s"${name.toUpperCase()}_$suffix", throw new Exception(
      s"${name}_$suffix not found"
    ))
  }

  def setup(config: Config) = {

    def sink(tx: FullTX) = Task {
      logger.info(tx.data.hash)
    }

    val network = config.network

    lazy val prodScheduler = Scheduler.io(name = s"$network-prod")
    lazy val consumerScheduler = Scheduler.io(name = s"$network-consumer")

    implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] =
      AsyncHttpClientMonixBackend()

    val nodes = config.nodes.zip(Range(0, config.nodes.size, 1))
      .map { case (uri, id) => Web3Node(s"${config.network}$id", uri) }

    val cluster = Cluster(nodes)
    val dispatcher = TXDispatcher(config.network,
      AggressiveLifter(cluster),
      sink,
      InMemoryTXPersistence(),
      retryPersistence)

    val blockDispatcher = BlockDispatcher(network, dispatcher,
      retriever,
      InMemoryBlockOffset())

    for {
      ch <- MVar.empty[Seq[FullBlock[ShallowTX]]]
      initialized <- blockDispatcher.init
      producer = nodes.head.subscribeBlocks(ch).executeOn(prodScheduler)
      consumer = BlockDispatcher.consumer(ch, initialized).executeOn(consumerScheduler)
      both <- Task.parMap2(producer, consumer) { case (_, r) => r }
    } yield both
  }

}
