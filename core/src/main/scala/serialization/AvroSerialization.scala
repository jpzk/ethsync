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

import java.io.ByteArrayOutputStream

import com.reebo.ethsync.core.Protocol.FullTX
import com.sksamuel.avro4s.{AvroOutputStream, SchemaFor, ToRecord}

import scala.util.Try

object AvroHelper {
  // Converting Ethereum hex encoding to Byte Array
  def hex2ByteArray(hex: String): Array[Byte] = {
    BigInt(hex.drop(2), 16).toByteArray
  }
}

object AvroSerialization {

  import Schemas._

  def serialize[T: SchemaFor : ToRecord](tx: FullTX, t: FullTransaction => T) = for {
    txn <- Transformer.json2Transaction(tx.data.data)
    receipt <- Transformer.json2Receipt(tx.receipt)
  } yield {
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[T](baos)
    output.write(t(FullTransaction(txn, receipt)))
    output.close()
    baos.toByteArray
  }

  def compact(tx: FullTX): Try[Array[Byte]] =
    serialize[CompactTransaction](tx, FullTransaction2CompactTransaction)

  def full(tx: FullTX): Try[Array[Byte]] =
    serialize[FullTransaction](tx, identity)
}
