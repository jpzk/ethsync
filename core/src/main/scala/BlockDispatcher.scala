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
import com.typesafe.scalalogging.LazyLogging
import monix.eval.{MVar, Task}
import monix.execution.atomic.AtomicLong

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object BlockDispatcher extends LazyLogging {

  /**
    * Consume block by block, changing the block dispatcher state
    *
    * @param blocks
    * @param dis
    * @return
    */
  def consumeBlocks(blocks: Seq[FullBlock[ShallowTX]], dis: BlockDispatcher):
  Task[BlockDispatcher] = blocks match {
    case xs :: tail => for {
      _ <- Task.now(logger.info("Consuming block", xs))
      dis <- dis.dispatchBlock(xs)
      ret <- consumeBlocks(tail, dis)
    } yield ret
    case Nil => Task.now(dis)
  }

  /**
    * Takes blocks from MVar producer-consumer bridge or sets
    * a tick to TXDispatcher to retry fetching transaction receipts.
    *
    * @param ch
    * @param dis
    * @return
    */
  def consumer(ch: MVar[Seq[FullBlock[ShallowTX]]], dis: BlockDispatcher):
  Task[Unit] = {
    for {
      _ <- Task.now(logger.info("Block dispatcher waiting for blocks"))
      blocks <- ch.take
      dispatcher <- consumeBlocks(blocks.sortBy(_.data.number), dis)
      run <- consumer(ch, dispatcher)
    } yield run
  }
}

/**
  * Retrieves new incoming blocks; but it is also checking if there are blocks
  * missing between the last time it was running and then fetches those blocks.
  */
case class BlockDispatcher(id: String,
                           tXDispatcher: TXDispatcher,
                           retriever: BlockRetriever,
                           persistence: BlockOffsetPersistence,
                           offset: Long = -1L) extends LazyLogging {

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
        this.persistence.setLast(block.data.number).flatMap {
          _ =>Task(this.copy(tXDispatcher = r))
        }
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

trait BlockOffsetPersistence {
  def setLast(height: Long): Task[Unit]

  def getLast: Task[Long]
}

trait BlockRetriever {
  def getBlock(height: Long): Task[FullBlock[ShallowTX]]
}

case class InMemoryBlockOffset() extends BlockOffsetPersistence with LazyLogging {
  val offset = AtomicLong(0)

  override def setLast(height: Long): Task[Unit] = Task {
    offset.getAndSet(height)
    logger.info(s"Set new offset to $height")
  }

  override def getLast: Task[Long] = Task {
    val v = offset.get
    logger.info(s"Got latest offset $v")
    v
  }
}

