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

import ClusterProtocol._
import Protocol._
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task

import scala.language.implicitConversions
import scala.util.{Failure, Random, Success, Try}

/**
  * Describes a cluster of Ethereum clients
  *
  * @param nodes
  */
case class Cluster(nodes: Seq[Node] = Seq()) extends LazyLogging {

  def addNodes(n: Seq[Node]): Cluster = this.copy(nodes ++ n)

  override def toString = s"Nodes: ${nodes.map(_.id)}"

  /**
    * Lifting a shallow transaction to a full transaction
    *
    * @param node node to use when lifting transaction
    * @param tx shallow tx to lift to full tx
    * @return
    */
  def liftTX(node: Node, tx: ShallowTX): Task[NodeResponse[FullTX]] = {
    val hash = tx.data.hash
    logger.debug(s"lifting $hash with ${node.id}")
    for {
      receipt <- node.getTransactionReceipt(tx.data.hash)
      res <- receipt match {
        case Success(r) =>
          logger.debug(s"lifted $hash with ${node.id}")
          Task.now(FullTX(tx.data, r))
        case Failure(e) =>
          logger.error(s"could not lift $hash with ${node.id}", e)
          Task.raiseError(new Exception("Could not get transaction receipt"))
      }
    } yield NodeResponse(node, res)
  }
}

/**
  * TX lifter with optimistic behavior. Asks only one registred node,
  * random selection, for transaction receipt for transaction. If it
  * cannot find raise error in Task.
  *
  * @param cluster
  */
case class ClusterRandomLifter(cluster: Cluster) extends TXLifter {

  def selection(nodes: Seq[Node]): Node = nodes(Random.nextInt(nodes.size))

  def select[A](f: Node => Task[A]): Task[A] = for {
    node <- Task(selection(cluster.nodes))
    run <- f(node)
  } yield run

  override def lift(tx: ShallowTX): Task[Try[FullTX]] = {
    select { node => cluster.liftTX(node, tx) }
      .map(_.response)
      .materialize
  }
}

/**
  * TX lifter with aggressive behavior; asks all the registered nodes
  * in the cluster for transaction receipt. If no node replies then
  * raise error in Task.
  *
  * @param cluster
  */
case class AggressiveLifter(cluster: Cluster) extends TXLifter {
  override def lift(tx: ShallowTX): Task[Try[FullTX]] = {
    val tasks = cluster.nodes.map { node =>
      cluster.liftTX(node, tx)
        .map(_.response)
        .materialize
    }
    for {
      results <- Task.gatherUnordered(tasks)
      successful <- Task(results.flatMap(_.toOption))
    } yield {
      if (successful.nonEmpty)
        Success(successful.head)
      else
        Failure(new Exception("No node responded with receipt"))
    }
  }
}


