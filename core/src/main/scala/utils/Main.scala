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
import com.reebo.ethsync.core.serialization.Schemas.FullTransaction
import com.reebo.ethsync.core.serialization.{AvroSerializer, Schemas, Transformer}
import com.reebo.ethsync.core.web3.{AggressiveLifter, Cluster, ClusterBlockRetriever, Web3Node}
import com.sksamuel.avro4s.RecordFormat
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.typesafe.scalalogging.LazyLogging
import monix.eval.{MVar, Task}
import monix.execution.Scheduler
import monix.kafka.{KafkaProducer, KafkaProducerConfig, Serializer}
import monix.reactive.Observable
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.duration._

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
    val name = config.name
    val metrics = new Metrics(name, config.graphite)
    logger.info(s"Starting setup for $name in network $network")

    lazy val prodScheduler = Scheduler.io(name = s"$name-$network-prod")
    lazy val consumerScheduler = Scheduler.io(name = s"$name-$network-consumer")
    lazy val kafkaScheduler = Scheduler.io(name = s"$name-$network-kafka")
    lazy val kafkaTXScheduler = Scheduler.io(name = s"$name-$network-kafka-tx")
    lazy val kafkaBlocksScheduler = Scheduler.io(name = s"$name-$network-kafka-blocks")

    val txSink: TXSink = setupSink(config.brokers, metrics,
      config.schemaRegistry, kafkaTXScheduler, config.txTopic)

    val blocksSink: BlockSink = setupBlockSink(config.brokers, metrics,
      config.schemaRegistry, kafkaBlocksScheduler, config.blocksTopic)

    val nodes = setupNodes(network, config.nodes)
    val cluster = Cluster(nodes)

    val txPersistence = new KafkaTXPersistence(name, kafkaScheduler, config.brokers)
    val tDispatcher = TXDispatcher(
      network,
      AggressiveLifter(cluster),
      txSink,
      txPersistence,
      BackoffRetry(10, 1.seconds),
      Some(metrics))

    val bDispatcher = BlockDispatcher(
      network,
      tDispatcher,
      ClusterBlockRetriever(cluster),
      new KafkaBlockOffset(name, kafkaScheduler, config.brokers),
      Some(blocksSink),
      Some(metrics))

    for {
      ch <- MVar.empty[Seq[FullBlock[ShallowTX]]]
      initialized <- bDispatcher.init
      producer = nodes.head.subscribeBlocks(ch).executeOn(prodScheduler)
      consumer = BlockConsumer.consumer(ch, initialized).executeOn(consumerScheduler)
      both <- Task.parMap2(producer, consumer) { case (_, r) => r }
    } yield both
  }

  private def setupBlockSink(brokers: Seq[String],
                             metrics: Metrics,
                             schemaRegistry: String,
                             scheduler: Scheduler,
                             topic: String) = new BlockSink {

    val producerCfg = KafkaProducerConfig.default.copy(bootstrapServers = brokers.toList)
    val serializerCfg = Map("schema.registry.url" -> schemaRegistry)
    implicit val serializer: Serializer[Object] = AvroSerializer.serializer(serializerCfg, false)
    private val producer = KafkaProducer[String, Object](producerCfg, scheduler)
    implicit val format = RecordFormat[Schemas.Block]
    val blockMeter = metrics.registry.meter("block-records-sent")

    override def sink(block: Protocol.FullBlock[ShallowTX]): Task[Unit] = (for {
      ll <- Task {logger.info(block.toString)}
      ftx <- Task.now(Transformer.transformBlock(block, identity))
      blockobj <- Task.now(ftx.get)
      record <- Task.now(new ProducerRecord[String, Object](topic, 0, "", format.to(blockobj)))
      _ <- producer.send(record)
    } yield {
      blockMeter.mark()
    }).onErrorHandleWith { e =>
      logger.error(e.getMessage, e)
      Task.raiseError(e)
    }
  }

  private def setupSink(brokers: Seq[String],
                        metrics: Metrics,
                        schemaRegistry: String,
                        scheduler: Scheduler,
                        topic: String) = new TXSink {

    val producerCfg = KafkaProducerConfig.default.copy(bootstrapServers = brokers.toList)
    val serializerCfg = Map("schema.registry.url" -> schemaRegistry)
    implicit val serializer: Serializer[Object] = AvroSerializer.serializer(serializerCfg, false)
    private val producer = KafkaProducer[String, Object](producerCfg, scheduler)
    implicit val format = RecordFormat[FullTransaction]
    val txMeter = metrics.registry.meter("tx-records-sent")

    override def sink(tx: Protocol.FullTX): Task[Unit] = (for {
      ftx <- Task.now(Transformer.transform(tx, identity))
      txobj <- Task.now(ftx.get)
      record <- Task.now(new ProducerRecord[String, Object](topic, 0, "", format.to(txobj)))
      _ <- producer.send(record)
    } yield {
      txMeter.mark()
    }).onErrorHandleWith { e =>
      logger.error(e.getMessage, e)
      Task.raiseError(e)
    }
  }

  private def setupNodes(networkId: String, nodes: Seq[String]): Seq[Web3Node] = {
    implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()
    nodes
      .zip(Range(0, nodes.size, 1))
      .map { case (uri, id) => Web3Node(s"$networkId-$id", uri) }
  }
}

case class Config(name: String,
                  networkId: String,
                  nodes: Seq[String],
                  brokers: Seq[String],
                  txTopic: String,
                  blocksTopic: String,
                  schemaRegistry: String,
                  graphite: Option[String])

object Config {
  def env(name: String): String =
    sys.env.getOrElse(s"${name.toUpperCase()}", throw new Exception(s"${name} not found"))

  def load: Config =
    Config(env("NAME"),
      env("NETWORK"),
      env("NODES").split(","),
      env("BROKERS").split(","),
      env("TXS_TOPIC"),
      env("BLOCKS_TOPIC"),
      env("SCHEMA_REGISTRY"),
      sys.env.get("GRAPHITE"))
}

