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

import com.reebo.ethsync.core.Protocol.{BlockData, FullBlock, ShallowTX}
import com.reebo.ethsync.core._
import com.reebo.ethsync.core.persistence.InMemoryBlockOffset
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class BlockConsumerSpec extends FlatSpec with MockFactory with Matchers {

  behavior of "BlockConsumer"

  it should "consume blocks and dispatch blocks to tx dispatcher" in {

    val network = "test"
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))
    (txDispatcher.schedule _).expects(List()).returning(txDispatcher)
    (txDispatcher.dispatch _).expects().returning(Task.now(txDispatcher))

    val retriever = mock[BlockRetriever]
    val blockDispatcher = BlockDispatcher(network, txDispatcher, retriever, InMemoryBlockOffset(0))
    val blocksSeq = Seq(
      FullBlock[ShallowTX](BlockData("0x0", 1L, 1L, "{}".asJson), Seq())
    )

    val t = for {
      _ <- blockDispatcher.persistence.setLast(1L)
      dis <- blockDispatcher.init
      blocks <- Task.now(blocksSeq)
      newDis <- BlockConsumer.consumeBlocks(blocks, dis)
    } yield {
      newDis.offset.shouldEqual(1L)
    }
    t.runSyncUnsafe(1.second)

  }

  it should "replay missing blocks and then dispatch latest blocks" in {

    val network = "test"
    val txDispatcher = mock[TXDispatcher]
    (txDispatcher.init _).expects().returns(Task.now(txDispatcher))
    (txDispatcher.schedule _).expects(List()).returning(txDispatcher).repeat(2)
    (txDispatcher.dispatch _).expects().returning(Task.now(txDispatcher)).repeat(2)

    val retriever = mock[BlockRetriever]
    val missingBlock = FullBlock[ShallowTX](BlockData("0x1", 1L, 1L, "{}".asJson), Seq())
    (retriever.getBlock _).expects(1L).returns(Task.now(missingBlock))

    val blockDispatcher = BlockDispatcher(network, txDispatcher, retriever, InMemoryBlockOffset())
    val blocksSeq = Seq(
      FullBlock[ShallowTX](BlockData("0x2", 2L, 1L, "{}".asJson), Seq())
    )

    val t = for {
      _ <- blockDispatcher.persistence.setLast(0L)
      dis <- blockDispatcher.init
      blocks <- Task.now(blocksSeq)
      newDis <- BlockConsumer.consumeBlocks(blocks, dis)
    } yield {
      newDis.offset.shouldEqual(2L)
    }
    t.runSyncUnsafe(1.second)
  }


}
