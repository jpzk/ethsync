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
import com.reebo.ethsync.core.Protocol.FullTX
import com.sksamuel.avro4s.{SchemaFor, ToRecord}
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, HCursor, Json}
import cats.Traverse
import cats.instances.try_._
import cats.instances.list._

import scala.util.{Failure, Success, Try}

object Transformer extends LazyLogging {

  import Schemas._
  import Validators._

  def json2Block(block: Json, transactions: Seq[Json]): Try[Block] = {
    val c = block.hcursor
    val f = for {
      blockHash <- decode(c, "hash", hash)
      blockNumber <- decode(c, "number", blockNumber)
      parent <- decode(c, "parentHash", hash)
      nonceField <- decode(c, "nonce", blockNonce)
      sha3uncles <- decode(c, "sha3Uncles", sha3uncles)
      logsBloom <- decode(c, "logsBloom", logsBloom)
      transactionRoot <- decode(c, "transactionsRoot", transactionsRoot)
      stateRoot <- decode(c, "stateRoot", stateRoot)
      receiptsRoot <- decode(c, "receiptsRoot", receiptsRoot)
      miner <- decode(c, "miner", address)
      difficultyField <- decode(c, "difficulty", difficulty)
      totalDifficulty <- decode(c, "totalDifficulty", difficulty)
      extraData <- decode(c, "extraData", extraData)
      size <- decode(c, "size", gas)
      gasLimit <- decode(c, "gasLimit", gas)
      gasUsed <- decode(c, "gasUsed", gas)
      timestamp <- decode(c, "timestamp", timestamp)
      uncles <- decode(c, "uncles", uncles)
      transactions <- Traverse[List].sequence(transactions.map(json2Transaction).toList)
    } yield Block(blockNumber, blockHash, parent, nonceField, sha3uncles, logsBloom, transactionRoot,
      receiptsRoot, stateRoot, miner, difficultyField, totalDifficulty, extraData, size, gasLimit, gasUsed, timestamp,
      uncles, transactions.toArray)
    f.recoverWith {
      case e: Exception =>
        logger.error(s"Error on block ${block.toString()}: ${e.getMessage}")
        Failure(e)
    }
  }

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

      // pre-byzantium fork there's no status field, setting default value for status field
      // to remain backwards compatibility.
      status <- decode(c, "status", status).recover { case _: Exception => -1 }
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

  /**
    * Validates the FullTX with validators and applys f to convert to another schema
    *
    * @param tx transaction
    * @param f converter function to T
    * @tparam T result type T
    */
  def transform[T: SchemaFor : ToRecord](tx: FullTX, f: FullTransaction => T) = for {
    txn <- Transformer.json2Transaction(tx.data.data)
    receipt <- Transformer.json2Receipt(tx.receipt)
  } yield f(FullTransaction(txn.blockNumber, tx.data.timestamp, txn, receipt))

  /**
    * Validates the block JSON and applies f to convert to another schema
    *
    * @param block
    * @param f
    * @tparam T
    * @return
    */
  def transformBlock[T: SchemaFor : ToRecord](block: FullBlock[ShallowTX], f: Schemas.Block => T) = for {
    block <- Transformer.json2Block(block.data.data, block.txs.map(_.data.data))
  } yield f(block)
}

