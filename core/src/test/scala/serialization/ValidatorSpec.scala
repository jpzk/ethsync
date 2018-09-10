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
package com.reebo.ethsync.core.test

import com.reebo.ethsync.core.serialization.Transformer
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import scala.util.{Failure, Success, Try}

class ValidatorSpec extends FlatSpec with Matchers with LazyLogging {

  import com.reebo.ethsync.core.CirceHelpers._

  it should "validate all receipts in fixtures as successful" in {
    validate("core/src/test/resources/receipts.json",
      Transformer.json2Receipt, expectedSuccess = true)
  }

  it should "invalidate all receipts in fixtures as unsuccessful" in {
    validate("core/src/test/resources/badinput_receipts.json",
      Transformer.json2Receipt, expectedSuccess = false)
  }

  it should "validate all transactions in fixtures as successful" in {
    validate("core/src/test/resources/txs.json",
      Transformer.json2Transaction, expectedSuccess = true)
  }

  it should "invalidate all transactions in fixtures as unsuccessful" in {
    validate("core/src/test/resources/badinput_txs.json",
      Transformer.json2Transaction, expectedSuccess = false)
  }

  def validate[T](filename: String, f: Json => Try[T], expectedSuccess: Boolean): Unit = {
    val res = Source.fromFile(filename).getLines().mkString("")

    handleDecodingErrorTry(logger, parse(res)) match {
      case Success(json) => json.asArray.get.foreach { j =>
        val result = f(j)
        result.isSuccess shouldEqual expectedSuccess
      }
      case Failure(e) => throw e
    }
  }
}
