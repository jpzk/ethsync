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
package com.reebo.ethsync.core

import com.reebo.ethsync.core.Protocol.{FullBlock, ShallowTX}
import com.typesafe.scalalogging.Logger
import io.circe.Json
import monix.eval.{MVar, Task}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.Try

object ClusterProtocol {

  case class NodeResponse[T](node: Node, response: T)

  trait Node {
    val id: String

    /**
      * Getting transaction receipt for a specific transaction hash
      *
      * @param hash
      * @return
      */
    def getTransactionReceipt(hash: String): Task[Try[Json]]

    /**
      * Subscribe task, it is a producer communicating via consumer
      * through MVar, run a on seperate scheduler.
      *
      * @param ch
      * @return producer running on supplied scheduler
      */
    def subscribeBlocks(ch: MVar[Seq[FullBlock[ShallowTX]]]): Task[Unit]
  }

}

object Protocol {
  type UndispatchedTXs = Seq[ShallowTX]

  case class FullTX(data: TXData, receipt: Json) extends TX {
    def toShallow = ShallowTX(data)

    override def toString: String = s"${data.hash} ${data.data} ${receipt}"
  }

  case class ShallowTX(data: TXData) extends TX

  case class TXData(hash: String, data: Json)

  sealed trait TX {
    val data: TXData
  }

  case class FullBlock[T <: TX](data: BlockData, txs: Seq[T])

  case class ShallowBlock(data: BlockData)

  case class BlockData(hash: String, number: Long, data: Json)

  sealed trait Block {
    val data: BlockData
  }

}

object EthRequests {

  case class RPCResponse[T](jsonrpc: String, id: Option[String], result: T)

  case class InternalError(code: Int, message: String)

  case class RPCError(jsonrpc: String, error: InternalError)

  case class RPCRequest[T](jsonrpc: String, method: String, params: T, id: Option[String] = None)

  def getBlock(hash: String) = RPCRequest("2.0", "eth_getBlockByHash", (hash, true))

  def getTXReceipt(hash: String) = RPCRequest("2.0", "eth_getTransactionReceipt", Seq(hash))

  def subscribeBlocks = RPCRequest("2.0", "eth_newBlockFilter", (), Some("1"))

  def pollChanges(filterId: String) = RPCRequest("2.0", "eth_getFilterChanges", Seq(filterId), Some("2"))
}

case class RawBlock(hash: String, number: String, data: Json)

/**
  * Retry filter
  */
trait RetryFilter {
  def retry[A](logger: Logger, source: Task[A]): Task[A]
}

/**
  * Exponential backoff retry filter
  *
  * @param retries
  * @param delay
  */
case class BackoffRetry(retries: Int, delay: FiniteDuration)
  extends RetryFilter {

  def retry[A](logger: Logger, source: Task[A]): Task[A] =
    retryBackoff(logger, source)

  def retryBackoff[A](logger: Logger, source: Task[A],
                      retries: Int = retries,
                      delay: FiniteDuration = delay): Task[A] = {
    source.onErrorHandleWith {
      case ex: Exception =>
        if (retries > 0)
          retryBackoff(logger, source, retries - 1, delay * 2)
            .delayExecution(delay)
        else {
          logger.error(s"SEVERE: ${
            ex.getMessage
          }", ex)
          Task.raiseError(ex)
        }
    }
  }
}