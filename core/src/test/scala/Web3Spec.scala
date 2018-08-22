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

import com.reebo.ethsync.core.{RawBlock, Web3}
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import io.circe.generic.auto._
import io.circe.syntax._
import monix.execution.Scheduler.Implicits.global
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import scala.concurrent.duration._
import Fixtures._
import com.reebo.ethsync.core.EthRequests._

object Fixtures {

  def SubscriptionResponseOk(id: String, filterId: String): String =
    RPCResponse("2.0", Some(id), filterId).asJson.noSpaces

  def NewBlocksResponseOk(id: String, hashes: Seq[String]): String =
    RPCResponse("2.0", Some(id), hashes).asJson.noSpaces

  def NewBlockResponseOk(id: String, hash: String): String =
    RPCResponse("2.0", Some(id), hash).asJson.noSpaces

  def GetBlockResponseOk: String =
    Source.fromFile("core/src/test/resources/block.json").getLines().mkString("")

  val ErrorResponse: String =
    RPCError("2.0", InternalError(-1, "Some error message")).asJson.noSpaces
}

class Web3Spec extends FlatSpec with Matchers {

  behavior of "Web3"

  val w = Web3("")
  implicit val sttpBackend = AsyncHttpClientMonixBackend()

  it should "subscribe block updates should return filter when responds ok" in {
    val res = w.subscribe(Some(SubscriptionResponseOk("1", "1")))
    res.runSyncUnsafe(1.second).shouldEqual("1")
  }

  it should "get block" in {
    val response = GetBlockResponseOk
    val res = w.getBlockWithHash(
      "0x7fe9ec36b15ea1297ef66a3db6cf95172f51e161d41f03cb9528330387319769",
      inject = Some(response))
    res.runSyncUnsafe(1.second).isInstanceOf[RawBlock]
  }

  it should "get block hash (only one) when responds ok" in {
    val response = NewBlockResponseOk("1", "0x0")
    val res = w.getBlockUpdates("1", Some(response))
    res.runSyncUnsafe(1.second).shouldEqual(Seq("0x0"))
  }

  it should "get block hashes from json when responds ok" in {
    val hashes = Seq("0x1", "0x2")
    val response = NewBlocksResponseOk("1", hashes)
    val res = w.getBlockUpdates("1", Some(response))
    res.runSyncUnsafe(1.second).shouldEqual(hashes)
  }

  it should "when subscribing return task error when rpc error" in {
    val res = w.subscribe(Some(ErrorResponse))
    res.materialize.runSyncUnsafe(1.second).isFailure.shouldEqual(true)
  }

  it should "when polling return task error when rpc error" in {
    val res = w.getBlockUpdates("1", Some(ErrorResponse))
    res.materialize.runSyncUnsafe(1.second).isFailure.shouldEqual(true)
  }
}
