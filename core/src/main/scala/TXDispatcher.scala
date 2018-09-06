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

import com.reebo.ethsync.core.Protocol._
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task

import scala.language.implicitConversions
import scala.util.{Failure, Try}

/**
  * TXDispatcher is consuming from multiple Web3j producers. The dispatcher holds
  * a list of transactions which getReceivedLogs failed. It is persisted using the
  * persistence supplied by FailedTXPersistence trait.
  *
  * @param id               network name
  * @param lifter           transaction receipt lifter
  * @param sink             sink for returned transactions with receipts
  * @param persistence      persistence for in-execution transactions
  * @param persistenceRetry retry filter for persistence
  * @param txs              sequence fo in-execution transactions
  */
case class TXDispatcher(id: String,
                        lifter: TXLifter,
                        sink: TXSink,
                        persistence: TXPersistence,
                        persistenceRetry: RetryFilter,
                        txs: UndispatchedTXs = Seq()) extends LazyLogging {

  /**
    * Initializes the TXDispatcher; reads all shallow transactions from the underlying
    * persistence. In the standard scenario, should be called right after creating dispatcher.
    *
    * @return TXDispatcher
    */
  def init: Task[TXDispatcher] = for {
    fails <- persistence.readAll
  } yield this.copy(txs = fails)

  /**
    * Adds shallow txs to persistence, uses a retry filter. If it cannot reach persistence
    * it will block the whole TXDispatcher.
    *
    * @param txs
    * @return
    */
  def addToPersistence(txs: Seq[ShallowTX]): Task[Unit] =
    persistenceRetry.retry(logger, persistence.add(txs))

  /**
    * Dispatches full txs to sink; returns a sequence of txs which could not be
    * written to the sink.
    *
    * @param txs
    * @return sequence of txs, success when it could be written otherwise failure.
    */
  def dispatchToSink(txs: Seq[FullTX]): Task[Seq[(FullTX, Try[Unit])]] = {
    Task.gatherUnordered(txs.map {
      tx => sink.sink(tx).materialize.map { r => (tx, r) }
    })
  }

  private def logOutput(fullTxs: Seq[FullTX],
                        fails: Seq[ShallowTX],
                        failWrite: Seq[ShallowTX]) = {
    Task {
      logger.info(s"$id Success ${
        fullTxs.size
      }")
      logger.info(s"$id Failed ${
        fails.size
      } (receipt retrieval)")
      logger.info(s"$id Failed ${
        failWrite.size
      } (failed to write into sync)")
    }
  }

  /**
    * Tries to receive transaction logs for scheduled shallow transactions.
    * Returns new TXDispatcher with new scheduled shallow transactions (when failed)
    *
    * @return TXDispatcher or TaskError if cannot acknowledge scheduled transactions
    */
  def dispatch: Task[TXDispatcher] = for {
    results <- Task.gather(txs.map(lifter.lift)) // could fail IO
    fullTxs <- Task(results.flatMap(_.toOption))
    fails <- Task {
      results.zip(txs).flatMap {
        case (Failure(e), t) =>
          logger.error(e.getMessage)
          Some(t)
        case _ => None
      }
    }
    // could fail IO, put it into queue, to not block dispatcher
    dispatched <- dispatchToSink(fullTxs)

    // dispatched remove from persistence
    removeFromPersistence <- Task(dispatched
      .filter { case (_, r) => r.isSuccess }
      .map { case (tx, _) => tx.toShallow })

    // not so critical if IO fails here
    _ <- persistence.remove(removeFromPersistence).materialize

    // failed to write
    failedToDispatch <- Task(dispatched
      .filter { case (_, r) => r.isFailure }
      .map { case (tx, _) => tx.toShallow })

    retryTxs <- Task(fails ++ failedToDispatch)

    // could fail IO, critical retry filter;
    // if ultimately fail blockDispatcher will not ACK
    ok <- addToPersistence(retryTxs).materialize

    _ <- logOutput(fullTxs, fails, failedToDispatch)
    ret <- if (ok.isSuccess)
      Task(this.copy(txs = retryTxs))
    else
      Task.raiseError(new Exception("failed to add to persistence"))
  } yield ret

  def schedule(txs: UndispatchedTXs): TXDispatcher = {
    this.copy(txs = this.txs ++ txs)
  }
}


