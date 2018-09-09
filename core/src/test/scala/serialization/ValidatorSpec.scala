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

import com.reebo.ethsync.core.serialization.Validator
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import scala.util.Try

class ValidatorSpec extends FlatSpec with Matchers with LazyLogging {

  import com.reebo.ethsync.core.CirceHelpers._

  it should "validate all transactions in fixtures as successful" in {
    val res = Source.fromFile("core/src/test/resources/txs.json").getLines().mkString("")

    handleDecodingErrorTry(logger, parse(res)).flatMap { json =>
      Try(json.asArray.get)
    }.flatMap { arr =>
      Try(arr.foreach { j =>
        Validator.json2Transaction(j).isSuccess shouldEqual true
      })
    }
  }

  it should "invalidate all transactions in fixtures as unsuccessful" in {
    val res = Source.fromFile("core/src/test/resources/badinput_txs.json").getLines().mkString("")

    handleDecodingErrorTry(logger, parse(res)).flatMap { json =>
      Try(json.asArray.get)
    }.flatMap { arr =>
      Try(arr.foreach { j =>
        Validator.json2Transaction(j).isSuccess shouldEqual false
      })
    }
  }
}
