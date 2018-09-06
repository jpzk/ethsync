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
package com.reebo.ethsync.core.utils

import java.nio.ByteBuffer

import com.reebo.ethsync.core.Protocol.{FullBlock, FullTX, ShallowTX}
import com.reebo.ethsync.core._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.{MVar, Task}
import monix.execution.Scheduler
import monix.kafka.{KafkaProducer, KafkaProducerConfig}
import monix.reactive.Observable
import persistence.{KafkaBlockOffset, KafkaTXPersistence}
import web3.{AggressiveLifter, Cluster, ClusterBlockRetriever, Web3Node}

import scala.concurrent.duration._
import scala.language.implicitConversions

object Main extends App with LazyLogging {
  val mainScheduler = Scheduler.io(s"main")
  Task
    .gatherUnordered(Config.load.map { c => Setup.materialize(c).executeAsync })
    .onErrorHandle { err =>
      logger.error(err.getMessage)
    }
    .runOnComplete { _ =>
      logger.info("Shutdown.")
    }(mainScheduler)
}


object Setup extends LazyLogging {

  /**
    * Materialize Task for one network setup
    *
    * @param config
    * @return
    */
  def materialize(config: Config): Task[Unit] = {

    val network = config.network
    lazy val prodScheduler = Scheduler.io(name = s"$network-prod")
    lazy val consumerScheduler = Scheduler.io(name = s"$network-consumer")
    lazy val kafkaScheduler = Scheduler.io(name = s"$network-kafka")

    // Sink
    val producerCfg = KafkaProducerConfig.default.copy(
      bootstrapServers = config.kafka.toList
    )
    val producer = KafkaProducer[String, String](producerCfg, kafkaScheduler)

    def sink(tx: FullTX) = {
      logger.info(tx.data.hash)
      producer.send("transactions", tx.asJson.noSpaces)
        .map { _ => () }
    }

    val retryPersistence = BackoffRetry(10, 1.seconds)

    implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] =
      AsyncHttpClientMonixBackend()

    val nodes = config.nodes.zip(Range(0, config.nodes.size, 1))
      .map { case (uri, id) => Web3Node(s"${config.network}$id", uri) }

    val cluster = Cluster(nodes)
    val retriever = ClusterBlockRetriever(cluster)
    val dispatcher = TXDispatcher(config.network,
      AggressiveLifter(cluster),
      sink,
      new KafkaTXPersistence(kafkaScheduler, config.kafka),
      retryPersistence)

    val blockDispatcher =
      BlockDispatcher(network, dispatcher,
        retriever,
        new KafkaBlockOffset(kafkaScheduler, config.kafka)
      )

    for {
      ch <- MVar.empty[Seq[FullBlock[ShallowTX]]]
      initialized <- blockDispatcher.init
      producer = nodes.head.subscribeBlocks(ch).executeOn(prodScheduler)
      consumer = BlockConsumer.consumer(ch, initialized).executeOn(consumerScheduler)
      both <- Task.parMap2(producer, consumer) { case (_, r) => r }
    } yield both
  }
}

