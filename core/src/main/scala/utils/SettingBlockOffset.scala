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

import com.typesafe.scalalogging.LazyLogging
import monix.execution.Scheduler
import monix.kafka.Serializer.forJavaLong
import monix.kafka._

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
  val name = sys.env.getOrElse(s"NAME", throw new Exception(
    "Supply name"
  ))
  val offset = sys.env.getOrElse(s"OFFSET", throw new Exception(
    "Supply offset with OFFSET"
  ))
  val producerCfg = KafkaProducerConfig.default.copy(
    bootstrapServers = List(broker)
  )
  val producer = KafkaProducer[String, java.lang.Long](producerCfg, kafkaScheduler)
  producer.send(s"${name}-block-offset", offset.toLong)
    .map { _ => producer.close() }
    .runSyncUnsafe(1.minute)
    .runOnComplete { _ =>
      logger.info(s"Set block offset to ${offset.toLong}")
    }
}

