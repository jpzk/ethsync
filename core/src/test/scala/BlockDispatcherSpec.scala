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

import com.reebo.ethsync.core.Protocol.{BlockData, FullBlock, ShallowTX, TXData}
import com.reebo.ethsync.core._
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.util.Try

/**
  * The TXDispatcher needs to fulfill the following requirements
  */
class BlockDispatcherSpec extends FlatSpec with MockFactory with Matchers {

  case class TestParameters(blockDispatcher: Try[BlockDispatcher],
                            tXDispatcher: TXDispatcher,
                            retriever: BlockRetriever,
                            persistence: BlockOffsetPersistence)

  val timeout = 1.second

  behavior of "BlockDispatcher"

  it should "initialize the block offset with persistence" in successInit { task =>
    (for {
      dispatcher <- task.map(_.blockDispatcher)
    } yield {
      dispatcher.isSuccess.shouldEqual(true)
      dispatcher.get.offset.shouldEqual(1L)
    }).runSyncUnsafe(timeout)
  }

  it should "throw when cannot initialize block offset" in failInit { task =>
    (for {
      dispatcher <- task.map(_.blockDispatcher)
    } yield {
      dispatcher.isFailure.shouldEqual(true)
    }).runSyncUnsafe(timeout)
  }

  it should "increase offset in persistence and state when block acknowledged by tx dispatcher" in successDispatch { task =>
    (for {
      dispatcher <- task.map(_.blockDispatcher)
      lastOffset <- dispatcher.get.persistence.getLast
    } yield {
      dispatcher.get.offset.shouldEqual(1L)
      lastOffset.shouldEqual(1L)
    }).runSyncUnsafe(timeout)
  }

  it should "not increase offset when block not acknowledged by tx dispatcher" in failDispatch { task =>
    (for {
      dispatcher <- task.map(_.blockDispatcher)
      lastOffset <- dispatcher.get.persistence.getLast
    } yield {
      lastOffset.shouldEqual(0L) // should remain old offset
    }).runSyncUnsafe(timeout)
  }

  def failDispatch(test: Task[TestParameters] => Any): Unit = {
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))
    (txDispatcher.schedule _).expects(List()).returning(txDispatcher)
    (txDispatcher.dispatch _).expects().returning(Task.raiseError(new Exception("Some error.")))

    val retriever = mock[BlockRetriever]
    val persistence = InMemoryBlockOffset()
    val dispatcher = BlockDispatcher("id", txDispatcher, retriever, persistence)

    test(for {
      _ <- persistence.setLast(0L)
      dis <- dispatcher.init
      dis2 <- dis.dispatchBlock(FullBlock(genBlockData("hash", 1L), Seq())).materialize
    } yield TestParameters(dis2, txDispatcher, retriever, persistence))
  }

  def successDispatch(test: Task[TestParameters] => Any): Unit = {
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))
    (txDispatcher.schedule _).expects(List()).returning(txDispatcher)
    (txDispatcher.dispatch _).expects().returning(Task.now(txDispatcher))

    val retriever = mock[BlockRetriever]
    val persistence = InMemoryBlockOffset()
    val dispatcher = BlockDispatcher("id", txDispatcher, retriever, persistence)

    test(for {
      _ <- persistence.setLast(0L)
      dis <- dispatcher.init.materialize
      dis2 <- dis.get.dispatchBlock(FullBlock(genBlockData("hash", 1L), Seq())).materialize
    } yield TestParameters(dis2, txDispatcher, retriever, persistence))
  }

  def failInit(test: Task[TestParameters] => Any): Unit = {
    val txDispatcher = mock[TXDispatcher]
    val retriever = mock[BlockRetriever]
    val persistence = new BlockOffsetPersistence {
      override def setLast(height: Long): Task[Unit] = Task.unit

      override def getLast: Task[Long] = Task.raiseError(new Exception("Cannot get"))
    }
    val dispatcher = BlockDispatcher("id", txDispatcher, retriever, persistence)
    test(for {
      _ <- persistence.setLast(1L)
      dis <- dispatcher.init.materialize
    } yield TestParameters(dis, txDispatcher, retriever, persistence))
  }

  def successInit(test: Task[TestParameters] => Any): Unit = {
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))

    val retriever = mock[BlockRetriever]
    val persistence = InMemoryBlockOffset()
    val dispatcher = BlockDispatcher("id", txDispatcher, retriever, persistence)
    test(for {
      _ <- persistence.setLast(1L)
      dis <- dispatcher.init.materialize
    } yield TestParameters(dis, txDispatcher, retriever, persistence))
  }

  def switchReplay(test: Task[TestParameters] => Any): Unit = {
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))

    val retriever = mock[BlockRetriever]
    val persistence = InMemoryBlockOffset()
    val dispatcher = BlockDispatcher("id", txDispatcher, retriever, persistence)
    test(for {
      _ <- persistence.setLast(1L)
      dis <- dispatcher.init.materialize
    } yield TestParameters(dis, txDispatcher, retriever, persistence))
  }

  def scenario(tXDispatcher: TXDispatcher,
               retriever: BlockRetriever,
               persistence: BlockOffsetPersistence): Task[BlockDispatcher] = {

    val dispatcher = BlockDispatcher("id", tXDispatcher, retriever, persistence)
    dispatcher.init
  }

  def genBlockData(hash: String, offset: Long): BlockData = {
    BlockData(hash, offset, "{}".asJson)
  }

  def genShallowTX(hash: String): ShallowTX = {
    val data = new TXData(hash, "{}".asJson)
    ShallowTX(data)
  }
}

