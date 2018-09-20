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

import com.reebo.ethsync.core.CirceHelpers._
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, HCursor, Json}

import scala.util.{Failure, Success, Try}


object Transformer extends LazyLogging {

  import Schemas._
  import Validators._

  /**
    * Converting a transaction JSON (from JSON-RPC) into Transaction case class
    *
    * @param transaction as circe Json
    * @return transaction
    */
  def json2Transaction(transaction: Json): Try[Transaction] = {
    val c = transaction.hcursor
    val f = for {
      blockHash <- decode(c, "blockHash", hash)
      blockNumber <- decode(c, "blockNumber", blockNumber)
      from <- decode(c, "from", address)
      gasIn <- decode(c, "gas", gas)
      gasPrice <- decode(c, "gasPrice", gas)
      hash <- decode(c, "hash", hash)
      input <- decode(c, "input", input)
      nonce <- decode(c, "nonce", nonce)
      to <- decode(c, "to", optionalAddress)
      transactionIndex <- decode(c, "transactionIndex", TXIndex)
      value <- decode(c, "value", value)
      v <- decode(c, "v", v)
      r <- decode(c, "r", r)
      s <- decode(c, "s", s)
    } yield Transaction(
      blockHash, blockNumber, from, gasIn, gasPrice,
      hash, input, nonce, to, transactionIndex, value, v, r, s)
    f.recoverWith {
      case e: Exception =>
        logger.error(s"Error on transaction ${transaction.toString()}: ${e.getMessage}")
        Failure(e)
    }
  }

  /**
    * Converting a log JSON (from JSON-RPC) into Log
    *
    * @param log a circe Json
    * @return
    */
  def json2Log(log: Json): Try[Log] = {
    val c = log.hcursor
    val f = for {
      address <- decode(c, "address", address)
      topics <- decode(c, "topics", topics)
      data <- decode(c, "data", logData)
      blockNumber <- decode(c, "blockNumber", blockNumber)
      transactionHash <- decode(c, "transactionHash", hash)
      transactionIndex <- decode(c, "transactionIndex", TXIndex)
      blockHash <- decode(c, "blockHash", hash)
      logIndex <- decode(c, "logIndex", TXIndex)
      removed <- decode(c, "removed", booleanValue)
    } yield Log(address, topics, data, blockNumber, transactionHash,
      transactionIndex, blockHash, logIndex, removed)
    f.recoverWith {
      case e: Exception =>
        logger.error(s"Error on log ${log.toString()}: ${e.getMessage}")
        Failure(e)
    }
  }

  /**
    * Converting a receipt JSON (from JSON-RPC) into Receipt
    *
    * @param receipt as circe Json
    * @return receipt
    */
  def json2Receipt(receipt: Json): Try[Receipt] = {
    val c = receipt.hcursor
    val f = for {
      blockHash <- decode(c, "blockHash", hash)
      blockNumber <- decode(c, "blockNumber", blockNumber)
      contractAddress <- decode(c, "contractAddress", optionalAddress)
      cumulativeGasUsed <- decode(c, "cumulativeGasUsed", gas)
      from <- decode(c, "from", address)
      gasUsed <- decode(c, "gasUsed", gas)
      logs <- c.values match {
        case Some(vs) => collectTry(vs.map(json2Log)).map(_.toArray)
        case None => Success(Array[Log]())
      }
      logsBloom <- decode(c, "logsBloom", logsBloom)
      status <- decode(c, "status", status)
      hash <- decode(c, "transactionHash", hash)
      to <- decode(c, "to", optionalAddress)
      transactionIndex <- decode(c, "transactionIndex", TXIndex)
    } yield Receipt(
      blockHash, blockNumber, contractAddress, cumulativeGasUsed, from,
      gasUsed, logs, logsBloom, status, to, hash, transactionIndex
    )
    f.recoverWith {
      case e: Exception =>
        logger.error(s"Error on receipt ${receipt.toString()}: ${e.getMessage}")
        Failure(e)
    }
  }

  /**
    * Decoding a JSON field into the corresponding type with validator
    *
    * @param cursor    HCursor of JSON
    * @param downField fieldName in JSON
    * @param validator function that validates the field value
    * @tparam T the result type
    * @return T decoded field of type T
    */
  private def decode[V: Decoder, T](cursor: HCursor, downField: String,
                                    validator: (String, V) => Either[DomainValidation, T]): Try[T] = for {
    v <- handleDecodingErrorTry(logger, cursor.downField(downField).as[V])
    validated <- validator(downField, v).fold(
      { error => Failure(new Exception(error.errorMessage)) },
      { v => Success(v) }
    )
  } yield validated

  /**
    * Failure propagation for recursive structures when parsing
    *
    * @param iterable
    * @tparam T
    * @return
    */
  def collectTry[T](iterable: Iterable[Try[T]]): Try[Iterable[T]] = {
    if (iterable.forall(_.isSuccess))
      Success(iterable.map(_.get))
    else {
      Failure(iterable.filter(_.isFailure).head.failed.get)
    }
  }
}

