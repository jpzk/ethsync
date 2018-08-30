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

import com.reebo.ethsync.core.Protocol.ShallowTX
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.{Atomic, AtomicAny, AtomicLong}
import monix.kafka.Serializer.forJavaLong
import monix.kafka._
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
  * Console application to set the block offset
  */
object SettingBlockOffset extends App with LazyLogging {

  import monix.execution.Scheduler.Implicits.global

  lazy val kafkaScheduler = Scheduler.io(name = s"kafka")
  val broker = sys.env.getOrElse(s"KAFKA_BROKER", throw new Exception(
    "Supply kafka brokers"
  ))
  val offset = sys.env.getOrElse(s"OFFSET", throw new Exception(
    "Supply offset with OFFSET"
  ))
  val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = List(broker)
  )
  val producer = KafkaProducer[String, java.lang.Long](producerCfg, kafkaScheduler)
  producer.send("block-offset", offset.toLong)
    .map { _ => producer.close() }
    .runSyncUnsafe(1.minute)
    .runOnComplete { _ =>
      logger.info(s"Set block offset to ${offset.toLong}")
    }
}

/**
  * TXPersistence backed by Kafka, failover persistence for transactions in-flight,
  * for which the transaction receipt has not been retrieved.
  *
  * @param scheduler scheduler to run the Kafka producer on
  * @param brokers   sequence of Kafka brokers
  */
class KafkaTXPersistence(scheduler: Scheduler, brokers: Seq[String])
  extends KafkaBacked[Seq[ShallowTX], String](scheduler, brokers)
    with TXPersistence
    with LazyLogging {

  val GroupId = "tx-persistence"
  val Topic = "tx-persistence"
  val initialValue: String = "[]"
  val store: Atomic[Seq[ShallowTX]] = AtomicAny(Seq[ShallowTX]())

  private val consumerCfg = KafkaConsumerConfig.default.copy(
    bootstrapServers = brokers.toList,
    groupId = GroupId
  )
  private val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = brokers.toList
  )

  private val producer = KafkaProducer[String, String](producerCfg, scheduler)
  private val topicParititon = new TopicPartition(Topic, 0)

  override def add(txs: Seq[ShallowTX]): Task[Unit] = if (txs.isEmpty) Task.unit else for {
    _ <- Task.now(store.transform { set =>
      (set.toSet ++ txs).toSeq
    })
    _ <- producer.send(Topic, store.get.asJson.noSpaces)
  } yield {
    logger.info(s"Added ${store.get.size} to Kafka TX persistence")
  }

  override def remove(txs: Seq[ShallowTX]): Task[Unit] = if(txs.isEmpty) Task.unit else for {
    oldStore <- Task.now(store.get)
    _ <- Task.now(store.transform { set =>
      (set.toSet -- txs).toSeq
    })
    _ <- { // only send on change
      if (oldStore.toSet != store.get.toSet)
        producer.send(Topic, store.get.asJson.noSpaces)
      else Task.unit
    }
  } yield {
    logger.info(s"Removed ${store.get.size} to Kafka TX persistence")
  }

  // read latest from Kafka
  override def readAll: Task[Seq[ShallowTX]] = for {
    consumer <- KafkaConsumerObservable.createConsumer[String, String](consumerCfg, List(Topic))
    last <- seekToLastAndConsume(consumer, producer, topicParititon, Topic, initialValue)
    json <- handleError(parse(last))
    value <- handleError(json.as[Seq[ShallowTX]])
    _ <- Task(consumer.close())
  } yield {
    store.transform { _ => value } // update store
    store.get
  }

  def handleError[T](either: Either[io.circe.Error, T]): Task[T] = {
    val onError = (error: io.circe.Error) => {
      logger.error(s"Decoding error ${error.getMessage}")
      Task.raiseError(new Exception(error.getMessage))
    }
    either.fold(onError, Task.now)
  }
}

/**
  * BlockOffsetPersistence backed by Kafka. Storing the latest committed block
  * offset. Block which has been processed (or acknowledged to be processed).
  *
  * @param scheduler scheduler to run the Kafka producer on
  * @param brokers   sequence of Kafka brokers
  */
class KafkaBlockOffset(scheduler: Scheduler, brokers: Seq[String])
  extends KafkaBacked[Long, java.lang.Long](scheduler, brokers)
    with BlockOffsetPersistence
    with LazyLogging {

  val store = AtomicLong(0L)
  val initialValue = new java.lang.Long(0L)
  val GroupId: String = "block-offset"
  val Topic: String = "block-offset"

  private val consumerCfg = KafkaConsumerConfig.default.copy(
    bootstrapServers = brokers.toList,
    groupId = GroupId
  )
  private val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = brokers.toList
  )

  private val producer = KafkaProducer[String, java.lang.Long](producerCfg, scheduler)
  private val topicParititon = new TopicPartition(Topic, 0)

  // @todo propagate exceptions to Task
  override def setLast(height: Long): Task[Unit] =
    producer.send(Topic, height) map { _ => () }

  override def getLast: Task[Long] = for {
    consumer <- KafkaConsumerObservable.createConsumer[String, java.lang.Long](consumerCfg, List(Topic))
    res <- seekToLastAndConsume(consumer, producer, topicParititon, Topic, initialValue)
    _ <- Task(store.set(res))
  } yield {
    logger.info(s"Got last block offset from persistence $res")
    res
  }
}

/**
  * Super class of Kafka backed persistences
  *
  * @param scheduler
  * @param brokers
  * @tparam V  type of store
  * @tparam V2 type of how it's stored in Kafka
  */
class KafkaBacked[V, V2: Serializer : Deserializer](scheduler: Scheduler, brokers: Seq[String]) {
  self: LazyLogging =>

  def seekToLastAndConsume(consumer: KafkaConsumer[String, V2],
                           producer: KafkaProducer[String, V2],
                           topicParititon: TopicPartition,
                           topic: String,
                           initialValue: V2): Task[V2] = for {

    offset <- Task.now(consumer.endOffsets(List(topicParititon).asJava).asScala.get(topicParititon))
    last <- offset match {
      case None =>
        logger.info(s"No consumer offset set, will initial with ${initialValue}")
        producer.send(topic, initialValue).map { _ =>
          consumer.unsubscribe()
          consumer.assign(List(topicParititon).asJava)
          val ret = consumer.poll(1000).iterator().next.value() // @todo failure case
          consumer.commitSync()
          ret
        }
      case Some(o) => Task {
        logger.info(s"Got consumer offset ${o}")
        consumer.unsubscribe()
        consumer.assign(List(topicParititon).asJava)
        consumer.seek(topicParititon, o - 1)
        val ret = consumer.poll(1000).iterator().next().value() // @todo failure case
        consumer.commitSync()
        ret
      }
    }
  } yield last
}

