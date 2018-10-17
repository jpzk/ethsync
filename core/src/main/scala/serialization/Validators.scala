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

  def uncles(field: String, hexes: Array[String]): Either[DomainValidation, Array[String]] =
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

  def value(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    c <- Right(hex2BigInt(v).toString)
  } yield c

  def difficulty(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    c <- Right(hex2BigInt(v).toString)
  } yield c

  def hash(field: String, hash: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hash), hash, NotHash(field, hash))
    v <- Either.cond(isXBytes(hash, 32), hash, IsNotXBytes(field, hash, 32))
  } yield v

  def transactionsRoot(field: String, hash: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hash), hash, NotHash(field, hash))
    v <- Either.cond(isXBytes(hash, 32), hash, IsNotXBytes(field, hash, 32))
  } yield v

  def stateRoot(field: String, hash: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hash), hash, NotHash(field, hash))
    v <- Either.cond(isXBytes(hash, 32), hash, IsNotXBytes(field, hash, 32))
  } yield v

  def receiptsRoot(field: String, hash: String): Either[DomainValidation, String] = for {
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

 def size(field: String, hex: String): Either[DomainValidation, Int] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 4), hex, DoesNotFitXBytes(field, hex, 4))
    c <- Right(hex2Int(v))
  } yield c

  def gas(field: String, hex: String): Either[DomainValidation, Long] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 8), hex, DoesNotFitXBytes(field, hex, 8))
    c <- Right(hex2Long(v))
  } yield c

  def timestamp(field: String, hex: String): Either[DomainValidation, Long] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 8), hex, DoesNotFitXBytes(field, hex, 8))
    c <- Right(hex2Long(v))
  } yield c

  // @todo add more validation, size
  def input(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
  } yield v

  def extraData(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
  } yield v

  def logData(field: String, hex: String): Either[DomainValidation, String] = for {
    v <- Either.cond(isHex(hex), hex, NotHash(field, hex))
  } yield v

  def nonce(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 4), hex, DoesNotFitXBytes(field, hex, 4))
  } yield v

  def blockNonce(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(isXBytes(hex, 8), hex, IsNotXBytes(field, hex, 8))
  } yield v

  def sha3uncles(field: String, hex: String): Either[DomainValidation, String] = for {
    _ <- Either.cond(isHex(hex), hex, NotHash(field, hex))
    v <- Either.cond(fitsXBytes(hex, 32), hex, DoesNotFitXBytes(field, hex, 32))
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

object ValidatorHelpers {

  def isHex(str: String): Boolean = str.matches("0x[0-9A-Fa-f]*")

  def hex2X[T](hex: String, f: BigInt => T) = f(BigInt(hex.drop(2), 16))

  def hex2Long(hex: String): Long = hex2X(hex, _.toLong)

  def hex2BigInt(hex: String): BigInt = hex2X(hex, identity)

  def hex2Int(hex: String): Int = hex2X(hex, _.toInt)

  def hex2Byte(hex: String): Byte = hex2X(hex, _.toByte)

  def hex2Bytes(hex: String): Array[Byte] = hex2X(hex, _.toByteArray)

  def fitsXBytes(hex: String, bytes: Int): Boolean = hex.drop(2).length <= bytes * 2

  def isXBytes(hex: String, bytes: Int): Boolean = hex.drop(2).length == bytes * 2
}