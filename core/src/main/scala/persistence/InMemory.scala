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
package com.reebo.ethsync.core.persistence

import com.reebo.ethsync.core.Protocol.ShallowTX
import com.reebo.ethsync.core.{BlockOffsetPersistence, TXPersistence}
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.execution.atomic.{Atomic, AtomicLong}

import scala.collection.mutable
import scala.language.implicitConversions

/**
  * In-memory persistence for testing the TXDispatcher
  */
case class InMemoryTXPersistence() extends TXPersistence with LazyLogging {
  val store = Atomic(mutable.Set[ShallowTX]())

  override def add(txs: Seq[ShallowTX]): Task[Unit] = Task {
    txs.foreach { tx =>
      store.getAndTransform { s => s.add(tx); s }
    }
    logger.info(s"Added ${txs.size} txs to in-memory tx store")
  }

  override def remove(txs: Seq[ShallowTX]): Task[Unit] = Task {
    txs.foreach { tx =>
      store.getAndTransform { s => s.remove(tx); s }
    }
    logger.info(s"Removed ${txs.size} txs from in-memory tx store")
  }

  override def readAll: Task[Seq[ShallowTX]] = Task {
    val txs = store.get.toSeq
    logger.info(s"Read ${txs.size} from in-memory tx store")
    txs
  }
}

/**
  * In-memory persistence for testing the BlockDispatcher
  *
  * @param initial
  */
case class InMemoryBlockOffset(initial: Long = 0)
  extends BlockOffsetPersistence with LazyLogging {

  val offset = AtomicLong(initial)

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

