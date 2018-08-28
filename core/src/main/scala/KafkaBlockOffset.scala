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
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Kafka persistence for block offset
  *
  * @param scheduler
  */
case class KafkaBlockOffset(scheduler: Scheduler) extends BlockOffsetPersistence with LazyLogging {

  import monix.kafka.Serializer.forJavaLong

  val offsetStore = AtomicLong(0L)

  val consumerCfg = KafkaConsumerConfig.default.copy(
    bootstrapServers = List("kafka:9092"),
    groupId = "block-offset"
  )
  val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = List("kafka:9092")
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
    _ <- consumer.close()
  } yield last

  override def setLast(height: Long): Task[Unit] =
    producer.send("block-offset", height) map { _ => () }

  override def getLast: Task[Long] = {
    seekToLastAndConsume.map(_ => offsetStore.get)
  }
}
