package com.reebo.ethsync.core.serialization

import io.circe.Json

import scala.util.Try

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
                         gas: Int,
                         gasPrice: Int,
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

object Validator {

  import Schemas._

  def transactionJson2Transaction(transaction: Json): Try[Transaction] = ???

  def receiptJson2Receipt(receipt: Json): Try[Receipt] = ???
}
