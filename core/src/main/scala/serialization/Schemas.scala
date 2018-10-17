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
  * The case classes here are used for Avro serialization
  */
object Schemas {

  case class FullTransaction(blockNumber: Long, minedAt: Long, tx: Transaction, receipt: Receipt)

  case class Block(blockNumber: Long,
                   blockHash: String,
                   parentHash: String,
                   nonce: String,
                   sha3uncles: String,
                   logsBloom: String,
                   receiptsRoot: String,
                   transactionRoot: String,
                   stateRoot: String,
                   miner: String,
                   difficulty: String,
                   totalDifficulty: String,
                   extraData: String,
                   size: Long,
                   gasLimit: Long,
                   gasUsed: Long,
                   timestamp: Long,
                   transactions: Array[Transaction],
                   uncles: Array[String])

  case class Log(address: String,
                 logTopics: Array[String],
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
                     fromAddr: String,
                     gasUsed: Long,
                     logs: Array[Log],
                     logsBloom: String,
                     status: Int,
                     toAddr: Option[String],
                     transactionHash: String,
                     transactionIndex: Int)

  case class Transaction(blockHash: String,
                         blockNumber: Long,
                         fromAddr: String,
                         gas: Long,
                         gasPrice: Long,
                         hash: String,
                         input: String,
                         nonce: String,
                         toAddr: Option[String],
                         transactionIndex: Int,
                         weiValue: String,
                         v: Byte,
                         r: String,
                         s: String)
}
