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

import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.atomic.AtomicLong
import monix.kafka.{KafkaConsumerConfig, KafkaConsumerObservable, KafkaProducer, KafkaProducerConfig}
import monix.kafka.Serializer.forJavaLong
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Console application to set the block offset
  */
object SettingBlockOffset extends App with LazyLogging {
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
    .executeOn(kafkaScheduler)
}

/**
  * Kafka persistence for block offset
  *
  * @param scheduler
  */
case class KafkaBlockOffset(scheduler: Scheduler, brokers: Seq[String])
  extends BlockOffsetPersistence with LazyLogging {

  val offsetStore = AtomicLong(0L)

  val consumerCfg = KafkaConsumerConfig.default.copy(
    bootstrapServers = brokers.toList,
    groupId = "block-offset"
  )
  val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = brokers.toList
  )
  val producer = KafkaProducer[String, java.lang.Long](producerCfg, scheduler)
  val topicParititon = new TopicPartition("block-offset", 0)

  // seek to the last; if no last produce last 0L and commit
  def seekToLastAndConsume: Task[Long] = for {
    consumer <- KafkaConsumerObservable.createConsumer[String, java.lang.Long](consumerCfg, List("block-offset"))
    offset <- Task.now(consumer.endOffsets(List(topicParititon).asJava).asScala.get(topicParititon))
    last <- offset match {
      case Some(o) => Task {
        consumer.unsubscribe()
        consumer.assign(List(topicParititon).asJava)
        consumer.seek(topicParititon, o - 1)
        val ret = consumer.poll(1000).iterator().next().value()
        consumer.commitSync()
        logger.info(s"Set offset to $ret")
        offsetStore.getAndSet(ret)
      }
      case None =>
        producer.send("block-offset", new java.lang.Long(0L)).map { _ =>
          consumer.unsubscribe()
          consumer.assign(List(topicParititon).asJava)
          val ret = consumer.poll(1000).iterator().next.value()
          consumer.commitSync()
          logger.info(s"Set offset to $ret")
          offsetStore.getAndSet(ret)
        }
    }
    _ <- Task.now(consumer.close())
  } yield last

  override def setLast(height: Long): Task[Unit] =
    producer.send("block-offset", height) map { _ => () }

  override def getLast: Task[Long] = {
    seekToLastAndConsume.map(_ => offsetStore.get)
  }
}
