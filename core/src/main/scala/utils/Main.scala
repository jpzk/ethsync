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

import com.reebo.ethsync.core.Protocol.{FullBlock, ShallowTX}
import com.reebo.ethsync.core._
import com.reebo.ethsync.core.persistence.{KafkaBlockOffset, KafkaTXPersistence}
import com.reebo.ethsync.core.serialization.AvroSerialization
import com.reebo.ethsync.core.web3.{AggressiveLifter, Cluster, ClusterBlockRetriever, Web3Node}
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.typesafe.scalalogging.LazyLogging
import monix.eval.{MVar, Task}
import monix.execution.Scheduler
import monix.kafka.{KafkaProducer, KafkaProducerConfig}
import monix.reactive.Observable

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {

  def taskFactory = () => Setup.default(Config.load)

  graceRestartOnError(taskFactory)
    .runOnComplete { _ => logger.info("Shutdown.") }(Scheduler.io(s"main"))

  def graceRestartOnError(taskFactory: () => Task[Unit]): Task[Unit] = {
    def restartable(e: Exception): Task[Unit] = {
      logger.error(s"${e.getMessage} occured, will restart.", e)
      graceRestartOnError(taskFactory)
    }
    def severe(e: Exception): Task[Unit] = {
      logger.error(s"${e.getMessage} occured, it is SEVERE will not restart.", e)
      Task.raiseError(e)
    }

    taskFactory().onErrorRecoverWith {
      case e: java.net.ConnectException => restartable(e)
      case e: Exception => restartable(e)
    }
  }
}

object Setup extends LazyLogging {
  def default(config: Config): Task[Unit] = {
    val network = config.networkId
    logger.info(s"Starting setup for $network")

    lazy val prodScheduler = Scheduler.io(name = s"$network-prod")
    lazy val consumerScheduler = Scheduler.io(name = s"$network-consumer")
    lazy val kafkaScheduler = Scheduler.io(name = s"$network-kafka")

    val producer = kafka(config.brokers, kafkaScheduler)
    val sink: TXSink = setupSink(config.format, config.topic, producer)
    val nodes = setupNodes(network, config.nodes)
    val cluster = Cluster(nodes)

    val txPersistence = new KafkaTXPersistence(kafkaScheduler, config.brokers)
    val bDispatcher = BlockDispatcher(network,
      setupTXDispatcher(network, cluster, sink, txPersistence),
      ClusterBlockRetriever(cluster),
      new KafkaBlockOffset(kafkaScheduler, config.brokers))

    for {
      ch <- MVar.empty[Seq[FullBlock[ShallowTX]]]
      initialized <- bDispatcher.init
      producer = nodes.head.subscribeBlocks(ch).executeOn(prodScheduler)
      consumer = BlockConsumer.consumer(ch, initialized).executeOn(consumerScheduler)
      both <- Task.parMap2(producer, consumer) { case (_, r) => r }
    } yield both
  }

  private def setupSink(format: OutputFormat, topic: String,
                        producer: KafkaProducer[String, Array[Byte]]) = new TXSink {
    override def sink(tx: Protocol.FullTX): Task[Unit] = {
      (format match {
        case FullTransaction => AvroSerialization.full _
        case CompactTransaction => AvroSerialization.compact _
      }) (tx) match {
        case Success(bytes) => producer.send(topic, bytes).map { _ => () }
        case Failure(e) => Task.raiseError(e)
      }
    }
  }

  private def setupNodes(networkId: String, nodes: Seq[String]): Seq[Web3Node] = {
    implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()
    nodes
      .zip(Range(0, nodes.size, 1))
      .map { case (uri, id) => Web3Node(s"$networkId-$id", uri) }
  }

  private def setupTXDispatcher(networkId: String, cluster: Cluster, sink: TXSink, persistence: TXPersistence) =
    TXDispatcher(
      networkId,
      AggressiveLifter(cluster),
      sink,
      persistence,
      BackoffRetry(10, 1.seconds))

  private def kafka(brokers: Seq[String], scheduler: Scheduler) =
    KafkaProducer[String, Array[Byte]](KafkaProducerConfig.default.copy(brokers.toList), scheduler)
}

sealed trait OutputFormat

case object FullTransaction extends OutputFormat

case object CompactTransaction extends OutputFormat

case class Config(networkId: String,
                  nodes: Seq[String],
                  brokers: Seq[String],
                  topic: String,
                  format: OutputFormat)

object Config {
  def env(name: String): String = {
    sys.env.getOrElse(s"${name.toUpperCase()}", throw new Exception(s"${name} not found"))
  }

  def load: Config =
    Config(env("NAME"),
      env("NODES").split(","),
      env("BROKERS").split(","),
      env("TOPIC"),
      env("FORMAT") match {
        case "full" => FullTransaction
        case "compact" => CompactTransaction
        case _ => throw new Exception("Format not supported.")
      })
}
