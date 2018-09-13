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

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, HCursor, Json}
import com.reebo.ethsync.core.CirceHelpers._

import scala.util.{Failure, Success, Try}

object Transformer extends LazyLogging {

  import Validators._
  import Schemas._

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

/**
  * Validators for transaction and receipts
  */
object Validators {

  import ValidatorHelpers._

  sealed trait DomainValidation {
    def errorMessage: String
  }

  case class NotHash(field: String, value: String) extends DomainValidation {
    def errorMessage: String = s"Field $field with value $value is not hexadecimal."
  }

  case class DoesNotFitXBytes(field: String, value: String, bytes: Int) extends DomainValidation {
    def errorMessage: String = s"Field $field with value $value does not fit $bytes bytes."
  }

  case class IsNotXBytes(field: String, value: String, bytes: Int) extends DomainValidation {
    def errorMessage: String = s"Field $field with value $value is not $bytes bytes."
  }

  def booleanValue(field: String, b: Boolean): Either[DomainValidation, Boolean] = Right(b)

  def topics(field: String, hexes: Array[String]): Either[DomainValidation, Array[String]] =
    Either.cond(hexes.forall(isHex), hexes, NotHash(field, hexes.toString))

  def status(field: String, hex: String): Either[DomainValidation, Int] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 1), hex, DoesNotFitXBytes(field, hex, 1))
    c <- Right(hex2Int(v))
  } yield c

  def logsBloom(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(isXBytes(hex, 256), hex, IsNotXBytes(field, hex, 256))
  } yield v

  def value(field: String, hex: String): Either[DomainValidation, Long] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 16), hex, DoesNotFitXBytes(field, hex, 16))
    c <- Right(hex2Long(v))
  } yield c

  def hash(field: String, hash: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hash), hash, NotHash(field, hash))
    v <- Either.cond(isXBytes(hash, 32), hash, IsNotXBytes(field, hash, 32))
  } yield v

  def optionalAddress(field: String, hex: Option[String]): Either[DomainValidation, Option[String]] =
    hex match {
      case Some(h) =>
        for {
          _ <- Either.cond(isHex(h), h, NotHash(field, h))
          v <- Either.cond(isXBytes(h, 20), h, IsNotXBytes(field, h, 20))
          c <- Right(Some(v))
        } yield c
      case None => Right(None)
    }

  def address(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(isXBytes(hex, 20), hex, IsNotXBytes(field, hex, 20))
  } yield v

  def blockNumber(field: String, hex: String): Either[DomainValidation, Long] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 4), hex, DoesNotFitXBytes(field, hex, 4))
    c <- Right(hex2Long(v))
  } yield c

  def TXIndex(field: String, hex: String): Either[DomainValidation, Int] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 4), hex, DoesNotFitXBytes(field, hex, 4))
    c <- Right(hex2Int(v))
  } yield c

  def gas(field: String, hex: String): Either[DomainValidation, Long] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 8), hex, DoesNotFitXBytes(field, hex, 8))
    c <- Right(hex2Long(v))
  } yield c

  // @todo add more validation, size
  def input(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
  } yield v

  def logData(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
  } yield v

  def nonce(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 4), hex, DoesNotFitXBytes(field, hex, 4))
  } yield v

  def v(field: String, hex: String): Either[DomainValidation, Byte] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(isXBytes(hex, 1), hex, IsNotXBytes(field, hex, 1))
    c <- Right(hex2Byte(v))
  } yield c

  def r(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 32), hex, DoesNotFitXBytes(field, hex, 32))
  } yield v

  def s(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 32), hex, DoesNotFitXBytes(field, hex, 32))
  } yield v
}

object Schemas {

  case class FullTransaction(tx: Transaction, receipt: Receipt)

  case class Log(address: String,
                 topics: Array[String],
                 data: String,
                 blockNumber: Long,
                 transactionHash: String,
                 transactionIndex: Int,
                 blockHash: String,
                 logIndex: Int,
                 removed: Boolean)

  case class Receipt(blockHash: String,
                     blockNumber: Long,
                     contractAddress: Option[String],
                     cumulativeGasUsed: Long,
                     from: String,
                     gasUsed: Long,
                     logs: Array[Log],
                     logsBloom: String,
                     status: Int,
                     to: Option[String],
                     transactionHash: String,
                     transactionIndex: Int)

  case class Transaction(blockHash: String,
                         blockNumber: Long,
                         from: String,
                         gas: Long,
                         gasPrice: Long,
                         hash: String,
                         input: String,
                         nonce: String,
                         to: Option[String],
                         transactionIndex: Int,
                         value: Long,
                         v: Byte,
                         r: String,
                         s: String)

}

object ValidatorHelpers {

  def isHex(str: String): Boolean = str.matches("0x[0-9A-Fa-f]*")

  def hex2X[T](hex: String, f: BigInt => T) = f(BigInt(hex.drop(2), 16))

  def hex2Long(hex: String): Long = hex2X(hex, _.toLong)

  def hex2Int(hex: String): Int = hex2X(hex, _.toInt)

  def hex2Byte(hex: String): Byte = hex2X(hex, _.toByte)

  def hex2Bytes(hex: String): Array[Byte] = hex2X(hex, _.toByteArray)

  def fitsXBytes(hex: String, bytes: Int): Boolean = hex.drop(2).length <= bytes * 2

  def isXBytes(hex: String, bytes: Int): Boolean = hex.drop(2).length == bytes * 2
}
