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
import io.circe.{HCursor, Json}

import scala.util.{Failure, Success, Try}

object Validator extends LazyLogging {

  import com.reebo.ethsync.core.CirceHelpers._
  import ValidatorHelpers._
  import Schemas._

  /**
    * Decoding a JSON field into the corresponding type with validator
    *
    * @param cursor    HCursor of JSON
    * @param downField fieldName in JSON
    * @param validator function that validates the field value
    * @param converter function that converts into type
    * @tparam T the result type
    * @return T decoded field of type T
    */
  private def _decode[T](cursor: HCursor,
                downField: String,
                validator: String => Boolean,
                converter: String => T): Try[T] = {

    val msg = new Exception("Could not validate") // @todo more precise error msgs

    handleDecodingErrorTry(logger, cursor.downField(downField).as[String])
      .flatMap { v => if (validator(v)) Success(v) else Failure(msg) }
      .flatMap { v => Try(converter(v)) }
  }

  /**
    * Converting a transaction JSON (from JSON-RPC) into Transaction case class
    *
    * @param transaction as circe Json
    * @return transaction
    */
  def json2Transaction(transaction: Json): Try[Transaction] = {
    import TransactionValidation._
    import SharedValidation._

    def decode[T](f: String, v: String => Boolean, c: String => T) =
      _decode[T](transaction.hcursor, f, v, c)

    for {
      blockHash <- decode[String]("blockHash", hash, identity)
      blockNumber <- decode[Long]("blockNumber", blockNumber, hex2Long)
      from <- decode[String]("from", address, identity)
      gas <- decode[Long]("gas", gas, hex2Long)
      gasPrice <- decode[Long]("gasPrice", gasPrice, hex2Long)
      hash <- decode[String]("hash", hash, identity)
      input <- decode[String]("input", input, identity)
      nonce <- decode[String]("nonce", nonce, identity)
      to <- decode[String]("to", address, identity)
      transactionIndex <- decode[Int]("transactionIndex", TXIndex, hex2Int)
      value <- decode[Long]("value", { _ => true }, hex2Long) // @todo add validation
      v <- decode[Byte]("v", v, hex2Byte)
      r <- decode[String]("r", r, identity)
      s <- decode[String]("s", s, identity)
    } yield Transaction(
      blockHash, blockNumber, from, gas, gasPrice, hash, input, nonce,
      to, transactionIndex, value, v, r, s)
  }

  /**
    * Converting a receipt JSON (from JSON-RPC) into Receipt case class
    *
    * @param receipt as circe Json
    * @return receipt
    */
  def json2Receipt(receipt: Json): Try[Receipt] = {
    import ReceiptValidation._
    import SharedValidation._

    def decode[T](f: String, v: String => Boolean, c: String => T) =
      _decode[T](receipt.hcursor, f, v, c)

    for {
      blockHash <- decode[String]("blockHash", hash, identity)
      blockNumber <- decode[Long]("blockNumber", blockNumber, hex2Long)
      contractAddress <- decode[String]("contractAddress", contractAddress, identity)
      cumulativeGasUsed <- decode[Long]("cumulativeGasUsed", cumulativeGasUsed, hex2Long)
      from <- decode[String]("from", address, identity)
      gasUsed <- decode[Long]("gasUsed", gasUsed, hex2Long)
      //logs <- decode[Array[String]]("logs", logs, identity)
      logsBloom <- decode[String]("logsBloom", logsBloom, identity)
      status <- decode[Int]("status", status, hex2Int)
      hash <- decode[String]("hash", hash, identity)
      to <- decode[String]("to", address, identity)
      transactionIndex <- decode[Int]("transactionIndex", TXIndex, hex2Int)
    } yield Receipt(
      blockHash, blockNumber, contractAddress, cumulativeGasUsed, from,
      gasUsed, Array(), logsBloom, status, to, hash, transactionIndex
    )
  }
}

object ValidatorHelpers {

  def isHex(str: String): Boolean = str.matches("0x[0-9A-F]+")

  def hex2X[T](hex: String, f: BigInt => T) = f(BigInt(hex.drop(2), 16))

  def hex2Long(hex: String): Long = hex2X(hex, _.toLong)

  def hex2Int(hex: String): Int = hex2X(hex, _.toInt)

  def hex2Byte(hex: String): Byte = hex2X(hex, _.toByte)

  def hex2Bytes(hex: String): Array[Byte] = hex2X(hex, _.toByteArray)

  def fitsXBytes(hex: String, bytes: Int): Boolean =
    BigInt(hex.drop(2), 16).toByteArray.length <= bytes

  def isXBytes(hex: String, bytes: Int): Boolean =
    BigInt(hex.drop(2), 16).toByteArray.length <= bytes
}

object SharedValidation {

  import ValidatorHelpers._

  def hash(hash: String): Boolean =
    isHex(hash) && isXBytes(hash, 32)

  def address(address: String): Boolean =
    isHex(address) && isXBytes(address, 20)

  def blockNumber(number: String): Boolean =
    fitsXBytes(number, 4)

  def TXIndex(index: String): Boolean =
    isHex(index) && fitsXBytes(index, 4)

}

object ReceiptValidation {

  import ValidatorHelpers._

  def contractAddress(address: String): Boolean = true // @todo validation

  def cumulativeGasUsed(hex: String): Boolean = isHex(hex) && fitsXBytes(hex, 8)

  def gasUsed(hex: String): Boolean = isHex(hex) && fitsXBytes(hex, 8)

  def logs(hexes: Array[String]): Boolean = hexes.forall(isHex)

  def logsBloom(hex: String): Boolean = isHex(hex)

  def status(hex: String): Boolean = isHex(hex)
}

object TransactionValidation {

  import ValidatorHelpers._

  def gas(number: String): Boolean =
    fitsXBytes(number, 8)

  def gasPrice(number: String): Boolean =
    fitsXBytes(number, 8)

  def input(input: String): Boolean =
    isHex(input)

  def nonce(nonce: String): Boolean =
    isHex(nonce) && fitsXBytes(nonce, 4)

  def v(v: String): Boolean =
    isHex(v) && isXBytes(v, 1)

  def r(r: String): Boolean =
    isHex(r) && isXBytes(r, 32)

  def s(s: String): Boolean =
    isHex(s) && isXBytes(s, 32)

}

object Schemas {

  case class Receipt(blockHash: String,
                     blockNumber: Long,
                     contractAddress: String,
                     cumulativeGasUsed: Long,
                     from: String,
                     gasUsed: Long,
                     logs: Array[String],
                     logsBloom: String,
                     status: Int,
                     to: String,
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
                         to: String,
                         transactionIndex: Int,
                         value: Long,
                         v: Byte,
                         r: String,
                         s: String)

}
