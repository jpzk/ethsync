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
package com.reebo.ethsync.core.serialization

import com.reebo.ethsync.core.serialization.Schemas.FullTransaction
import com.sksamuel.avro4s.{SchemaFor, ToRecord}
import monix.eval.Task
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import monix.kafka.{Serializer => MonixSerializer}
import monix.kafka.{Deserializer => MonixDeserializer}
import collection.JavaConverters._

object AvroSerializer {
  def serializer(cfg: Map[String, String], isKey: Boolean): MonixSerializer[Object] =
    MonixSerializer[Object](
      className = "io.confluent.kafka.serializers.KafkaAvroSerializer",
      classType = classOf[KafkaAvroSerializer],
      constructor = _ => {
        val serializer = new KafkaAvroSerializer()
        serializer.configure(cfg.asJava, isKey)
        serializer
      }
    )

   def deserializer(cfg: Map[String, String], isKey: Boolean): MonixDeserializer[Object] =
    MonixDeserializer[Object](
      className = "io.confluent.kafka.serializers.KafkaAvroDeserializer",
      classType = classOf[KafkaAvroDeserializer],
      constructor = _ => {
        val deserializer = new KafkaAvroDeserializer()
        deserializer.configure(cfg.asJava, isKey)
        deserializer
      }
    )
}

object AvroHelper {
  // Converting Ethereum hex encoding to Byte Array
  def hex2ByteArray(hex: String): Array[Byte] = {
    BigInt(hex.drop(2), 16).toByteArray
  }
}

trait AvroSink {
  def sink[T: SchemaFor : ToRecord](record: T): Task[Unit]
}

trait AvroTransform {
  def transform[T: SchemaFor : ToRecord](tx: FullTransaction): T
}
