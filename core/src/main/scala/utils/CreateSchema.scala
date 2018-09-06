package com.reebo.ethsync.core.utils

import java.io.{File, PrintWriter}

import com.reebo.ethsync.core.serialization.{AvroTransaction, AvroTransactionCompact}
import com.typesafe.scalalogging.LazyLogging
import com.sksamuel.avro4s.AvroSchema

/**
  * Util application to create Avro schema files
  */
object CreateSchema extends App with LazyLogging {

  def writeToFile(content: String, filename: String): Unit = {
    val writer = new PrintWriter(new File(filename))
    writer.write(content)
    writer.close()
  }

  writeToFile(AvroSchema[AvroTransaction].toString(true), "avro/TransactionSchema.json")
  writeToFile(AvroSchema[AvroTransactionCompact].toString(true), "avro/TransactionCompactSchema.json")
}
