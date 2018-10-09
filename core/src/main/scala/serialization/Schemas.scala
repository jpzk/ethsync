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

object Schemas {

  def Log2CompactLog(log: Log) = CompactLog(log.logTopics, log.data, log.removed)

  def FullTransaction2CompactTransaction(tx: FullTransaction) = {
    val txd = tx.tx
    CompactTransaction(
      txd.blockHash,
      txd.blockNumber,
      txd.fromAddr,
      txd.hash,
      txd.input,
      txd.toAddr,
      txd.weiValue,
      tx.receipt.status,
      tx.receipt.contractAddress,
      tx.receipt.logs.map(Log2CompactLog),
      tx.receipt.logsBloom
    )
  }

  case class FullTransactionKey(partition: String)

  case class FullTransaction(blockNumber: Long, minedAt: Long, tx: Transaction, receipt: Receipt)

  case class CompactTransaction(blockHash: String,
                                blockNumber: Long,
                                fromAddr: String,
                                hash: String,
                                input: String,
                                toAddr: Option[String],
                                weiValue: Long,
                                status: Int,
                                contractAddress: Option[String],
                                logs: Array[CompactLog],
                                logsBloom: String)

  case class CompactLog(logTopics: Array[String], data: String, removed: Boolean)

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
                         weiValue: Long,
                         v: Byte,
                         r: String,
                         s: String)

}
