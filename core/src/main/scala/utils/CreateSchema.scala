package com.reebo.ethsync.core.utils

import java.io.{File, PrintWriter}

import com.reebo.ethsync.core.serialization.AvroTransactionCompact
import com.typesafe.scalalogging.LazyLogging
import com.sksamuel.avro4s.AvroSchema

/**
  * Util application to create Avro schema files
  */
object CreateSchema extends App with LazyLogging {

  def writeToFile(filename: String, content: String): Unit = {
    val writer = new PrintWriter(new File(filename))
    writer.write(content)
    writer.close()
  }

  writeToFile(AvroSchema[AvroTransactionCompact].toString(true), "avro/TransactionCompactSchema.json")
}
