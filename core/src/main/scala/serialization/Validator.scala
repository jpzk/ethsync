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

  /**
    * Returns true if hex value fits into specified bytes
    *
    * @param hex
    * @param bytes
    * @return
    */
  def fitsXBytes(hex: String, bytes: Int) =
    BigInt(hex.drop(2), 16).toByteArray.length <= bytes

  /**
    * Returns true if hex value is exactly specified bytes
    *
    * @param hex
    * @param bytes
    * @return
    */
  def isXBytes(hex: String, bytes: Int) =
    BigInt(hex.drop(2), 16).toByteArray.length <= bytes

}

object Validator extends LazyLogging with TransactionValidation with TransactionConverters {

  import com.reebo.ethsync.core.CirceHelpers._
  import Schemas._

  def json2Transaction(transaction: Json): Try[Transaction] = {

    val c = transaction.hcursor
    val msg = new Exception("Could not validate")

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
                  converter: String => Try[T]): Try[T] = {

      handleDecodingErrorTry(logger, c.downField(downField).as[String])
        .flatMap { v => if (validator(v)) Success(v) else Failure(msg) }
        .flatMap { v => converter(v) }
    }

    for {
      blockHash <- decode[String]("blockHash", validateHash, Try(_))
      blockNumber <- decode[Long]("blockNumber", validateBlockNumber, convertLong)
      from <- decode[String]("from", validateAddress, Try(_))
      gas <- decode[Long]("gas", validateGas, convertLong)
      gasPrice <- decode[Long]("gasPrice", validateGasPrice, convertLong)
      hash <- decode[String]("hash", validateHash, Try(_))
      input <- decode[String]("input", validateInput, Try(_))
      nonce <- decode[String]("nonce", validateNonce, Try(_))
      to <- decode[String]("to", validateAddress, Try(_))
      transactionIndex <- decode[Int]("transactionIndex", validateTXIndex, convertInt)
      value <- decode[Long]("value", { _ => true }, convertLong) // @todo add validation
      v <- decode[Byte]("v", validateV, convertByte)
      r <- decode[String]("r", validateR, Try(_))
      s <- decode[String]("s", validateS, Try(_))
    } yield {
      Transaction(
        blockHash,
        blockNumber,
        from,
        gas,
        gasPrice,
        hash,
        input,
        nonce,
        to,
        transactionIndex,
        value,
        v,
        r,
        s
      )
    }

  }

  def receiptJson2Receipt(receipt: Json): Try[Receipt] = ???
}

trait TransactionConverters {

  import ValidatorHelpers._

  def convertInt(str: String): Try[Int] = Try(hex2Int(str))

  def convertLong(str: String): Try[Long] = Try(hex2Long(str))

  def convertByte(byte: String): Try[Byte] = Try(hex2Byte(byte))
}

trait TransactionValidation {

  import ValidatorHelpers._

  def startsWith0x(str: String): Boolean = str.startsWith("0x")

  def validateHash(hash: String): Boolean = isXBytes(hash, 32)

  def validateAddress(address: String): Boolean = isXBytes(address, 20)

  def validateBlockNumber(number: String): Boolean = fitsXBytes(number, 4)

  def validateGas(number: String): Boolean = fitsXBytes(number, 8)

  def validateGasPrice(number: String): Boolean = fitsXBytes(number, 8)

  def validateInput(input: String) = true

  def validateNonce(nonce: String): Boolean = fitsXBytes(nonce, 4)

  def validateTXIndex(index: String): Boolean = fitsXBytes(index, 4)

  def validateV(v: String): Boolean = isXBytes(v, 1)

  def validateR(r: String): Boolean = isXBytes(r, 32)

  def validateS(s: String): Boolean = isXBytes(s, 32)

}