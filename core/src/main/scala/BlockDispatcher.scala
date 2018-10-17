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
import com.reebo.ethsync.core.utils.Metrics
import com.typesafe.scalalogging.LazyLogging
import monix.eval.{MVar, Task}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Consumer methods for new blocks passed via MVar
  */
object BlockConsumer extends LazyLogging {

  /**
    * Consume block by block, changing the block dispatcher state
    *
    * @param blocks blocks from MVar channel
    * @param dis    current block dispatcher state
    * @return
    */
  def consumeBlocks(blocks: Seq[FullBlock[ShallowTX]], dis: BlockDispatcher):
  Task[BlockDispatcher] = blocks match {
    case xs :: tail => for {
      _ <- Task.now(logger.info(s"Consuming block in ${dis.id}", xs))
      replayed <- Replay.until(dis, xs) // replay missing blocks (recursive)
      dis <- replayed.dispatchBlock(xs)
      ret <- consumeBlocks(tail, dis)
    } yield ret
    case Nil => Task.now(dis)
  }

  /**
    * Takes blocks from MVar producer-consumer bridge or sets
    * a tick to TXDispatcher to retry fetching transaction receipts.
    *
    * @param ch  communication channel for block updates
    * @param dis current state of block dispatcher
    * @return
    */
  def consumer(ch: MVar[Seq[FullBlock[ShallowTX]]], dis: BlockDispatcher):
  Task[Unit] = {
    for {
      _ <- Task.now(logger.info(s"Block dispatcher waiting for blocks in ${dis.id}"))
      blocks <- ch.take
      dispatcher <- consumeBlocks(blocks.sortBy(_.data.number), dis)
      run <- consumer(ch, dispatcher)
    } yield run
  }
}

/**
  * Retrieves new incoming blocks; but it is also checking if there are blocks
  * missing between the last time it was running and then fetches those blocks.
  *
  * @param id           network name
  * @param tXDispatcher transaction dispatcher to use
  * @param retriever    retriever for blocks (when need to replay blocks)
  * @param persistence  block offset persistence (fault tolerance)
  * @param offset       current block offset
  */
case class BlockDispatcher(id: String,
                           tXDispatcher: TXDispatcher,
                           retriever: BlockRetriever,
                           persistence: BlockOffsetPersistence,
                           metrics: Option[Metrics] = None,
                           offset: Long = -1L
                          ) extends LazyLogging {

  /**
    * Initializing block dispatcher; gets offset from persistence
    * and initializes TX dispatcher (retrieving tx from persistence)
    *
    * @return
    */
  def init: Task[BlockDispatcher] = for {
    offset <- this.persistence.getLast
    initializedTX <- this.tXDispatcher.init
    _ <- Task.now(logger.info("Block dispatcher initialized with offset", offset))
  } yield this.copy(tXDispatcher = initializedTX, offset = offset)

  /**
    * Dispatch a block
    *
    * @param block
    * @return
    */
  def dispatchBlock(block: FullBlock[ShallowTX]): Task[BlockDispatcher] = for {
    newDis <- dispatchTXs(block.txs)
    ret <- newDis match {
      case Success(r) =>
        for {
          _ <- this.persistence.setLast(block.data.number)
          _ <- Task {
            metrics.foreach {
              _.blockOffset = block.data.number
            }
          }
          _ <- sink match {
            case Some(s) => s.sink(block)
            case None => Task.unit
          }
          dispatch <- Task(this.copy(tXDispatcher = r, offset = block.data.number))
        } yield dispatch
      case Failure(e) =>
        logger.error("Could not acknowledge block; failure in TXdispatcher", e)
        Task(this)
    }
  } yield ret

  /**
    * Dispatches a block to a TXDispatcher, if it succeeds sets the offset, otherwise
    * it will not change the offset.
    *
    * @param txs
    * @return
    */
  def dispatchTXs(txs: Seq[ShallowTX]): Task[Try[TXDispatcher]] = for {
    sTxDispatcher <- Task(this.tXDispatcher.schedule(txs))
    newTxDispatcher <- sTxDispatcher.dispatch.materialize // could fail IO
  } yield newTxDispatcher
}

object Replay extends LazyLogging {

  /**
    * Recursive function to retrieve and dispatch block sequentially.
    *
    * @param m   sequence of missing blocks
    * @param dis current block dispatcher state
    * @return
    */
  def replay(dis: BlockDispatcher, m: Seq[Long]): Task[BlockDispatcher] = m match {
    case xs :: tail =>
      for {
        block <- dis.retriever.getBlock(xs)
        _ <- Task {
          logger.info(s"Retrieved block $xs for replay in ${dis.id}")
        }
        newDis <- dis.dispatchBlock(block)
        run <- replay(newDis, tail)
      } yield run
    case Nil =>
      Task.now(dis)
  }

  /**
    * This should a ordered, sequence fetching of blocks
    *
    * @return
    */
  def until(dis: BlockDispatcher, block: FullBlock[ShallowTX]): Task[BlockDispatcher] =
    dis.offset + 1 until block.data.number match {
      case m if m.nonEmpty => replay(dis, m.toList)
      case _ => Task.now(dis)
    }
}


