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
import io.circe.Json

import scala.util.{Failure, Success, Try}

object Validator extends LazyLogging with TransactionValidation {

  import com.reebo.ethsync.core.CirceHelpers._
  import ValidatorHelpers._
  import Schemas._

  def json2Transaction(transaction: Json): Try[Transaction] = {

    val c = transaction.hcursor
    val msg = new Exception("Could not validate") // @todo more precise error msgs

    /**
      * Decoding a JSON field into the corresponding type with validator
      *
      * @param downField fieldName in JSON
      * @param validator function that validates the field value
      * @param converter function that converts into type
      * @tparam T the result type
      * @return T decoded field of type T
      */
    def decode[T](downField: String,
                  validator: String => Boolean,
                  converter: String => T): Try[T] = {

      handleDecodingErrorTry(logger, c.downField(downField).as[String])
        .flatMap { v => if (validator(v)) Success(v) else Failure(msg) }
        .flatMap { v => Try(converter(v)) }
    }

    for {
      blockHash <- decode[String]("blockHash", validateHash, identity)
      blockNumber <- decode[Long]("blockNumber", validateBlockNumber, hex2Long)
      from <- decode[String]("from", validateAddress, identity)
      gas <- decode[Long]("gas", validateGas, hex2Long)
      gasPrice <- decode[Long]("gasPrice", validateGasPrice, hex2Long)
      hash <- decode[String]("hash", validateHash, identity)
      input <- decode[String]("input", validateInput, identity)
      nonce <- decode[String]("nonce", validateNonce, identity)
      to <- decode[String]("to", validateAddress, identity)
      transactionIndex <- decode[Int]("transactionIndex", validateTXIndex, hex2Int)
      value <- decode[Long]("value", { _ => true }, hex2Long) // @todo add validation
      v <- decode[Byte]("v", validateV, hex2Byte)
      r <- decode[String]("r", validateR, identity)
      s <- decode[String]("s", validateS, identity)
    } yield Transaction(
      blockHash, blockNumber, from, gas, gasPrice, hash, input, nonce,
      to, transactionIndex, value, v, r, s)
  }
}

object Schemas {

  case class Receipt(blockHash: String,
                     blockNumber: Long,
                     contractAddress: String,
                     cumulativeGasUsed: Int,
                     from: String,
                     gasUsed: Int,
                     logs: Array[String],
                     logsBloom: String,
                     status: Boolean,
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

object ValidatorHelpers {

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

trait TransactionValidation {

  import ValidatorHelpers._

  def isHex(str: String): Boolean = str.matches("0x[0-9A-F]+")

  def validateHash(hash: String): Boolean =
    isHex(hash) && isXBytes(hash, 32)

  def validateAddress(address: String): Boolean =
    isHex(address) && isXBytes(address, 20)

  def validateBlockNumber(number: String): Boolean =
    fitsXBytes(number, 4)

  def validateGas(number: String): Boolean =
    fitsXBytes(number, 8)

  def validateGasPrice(number: String): Boolean =
    fitsXBytes(number, 8)

  def validateInput(input: String): Boolean =
    isHex(input)

  def validateNonce(nonce: String): Boolean =
    isHex(nonce) && fitsXBytes(nonce, 4)

  def validateTXIndex(index: String): Boolean =
    isHex(index) && fitsXBytes(index, 4)

  def validateV(v: String): Boolean =
    isHex(v) && isXBytes(v, 1)

  def validateR(r: String): Boolean =
    isHex(r) && isXBytes(r, 32)

  def validateS(s: String): Boolean =
    isHex(s) && isXBytes(s, 32)

}