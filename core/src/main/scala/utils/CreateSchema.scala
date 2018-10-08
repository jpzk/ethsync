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

import java.io.{File, PrintWriter}

import com.reebo.ethsync.core.serialization.Schemas._
import com.sksamuel.avro4s.AvroSchema
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.syntax._

case class AvroSchemaImport(schema: String)

/**
  * Util application to create Avro schema files
  */
object CreateSchema extends App with LazyLogging {
  def importF(json: String) = AvroSchemaImport(json).asJson.toString()

  writeToFile(AvroSchema[Log].toString(false), "avro/LogImport.json", importF)
  writeToFile(AvroSchema[CompactLog].toString(false), "avro/CompactLogImport.json", importF)
  writeToFile(AvroSchema[FullTransaction].toString(false), "avro/FullTransactionImport.json", importF)
  writeToFile(AvroSchema[Transaction].toString(false), "avro/TransactionImport.json", importF)
  writeToFile(AvroSchema[CompactTransaction].toString(false), "avro/CompactTransactionImport.json", importF)
  writeToFile(AvroSchema[FullTransactionKey].toString(false), "avro/FullTransactionKeyImport.json", importF)

  writeToFile(AvroSchema[Log].toString(true), "avro/Log.json")
  writeToFile(AvroSchema[CompactLog].toString(true), "avro/CompactLog.json")
  writeToFile(AvroSchema[FullTransaction].toString(true), "avro/FullTransaction.json")
  writeToFile(AvroSchema[Transaction].toString(true), "avro/Transaction.json")
  writeToFile(AvroSchema[CompactTransaction].toString(true), "avro/CompactTransaction.json")
  writeToFile(AvroSchema[FullTransactionKey].toString(true), "avro/FullTransactionKey.json")

  def writeToFile(content: String, filename: String, w: (String => String) = identity): Unit = {
    val writer = new PrintWriter(new File(filename))
    writer.write(w(content))
    writer.close()
  }
}
