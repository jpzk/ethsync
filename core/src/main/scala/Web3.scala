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

import com.reebo.ethsync.core.ClusterProtocol.Node
import com.reebo.ethsync.core.EthRequests._
import com.reebo.ethsync.core.JSONDecoder._
import com.reebo.ethsync.core.Protocol._
import com.softwaremill.sttp._
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import monix.eval.{MVar, Task}

import scala.runtime.RichLong
import scala.concurrent.duration._
import scala.util.Try

/**
  * Abstraction of Ethereum client node using JSON-RPC, it is fault-tolerant e.g.
  * re-subscribes when subscription is lost.
  *
  * @param idName  identifier of node
  * @param url     url of node e.g. http://localhost:5454
  * @param backend HTTP backend
  */
case class Web3Node(idName: String, url: String)(implicit backend: HTTPBackend)
  extends Node with LazyLogging {

  override val id: String = idName
  val underlying = Web3(url)

  /**
    * Getting transaction receipt for a specific transaction hash
    *
    * @param hash transaction hash
    * @return
    */
  override def getTransactionReceipt(hash: String): Task[Try[Json]] =
    underlying.getTXReceipt(hash).materialize

  /**
    * Polls block updates in a loop until it retrieves updates
    *
    * @param filterId filter id, previously subscribe via underlying.subscribe()
    * @return sequence of block hashes that are new, since last poll
    */
  private def poll(filterId: String): Task[Seq[String]] = for {
    hashes <- underlying.getBlockUpdates(filterId)
    ret <- if (hashes.isEmpty) {
      poll(filterId).delayExecution(1.second)
    } else {
      Task {
        hashes
      }
    }
  } yield ret

  /**
    * Converts a RawBlock into FullBlock[ShallowTX]
    *
    * @param b
    * @return
    */
  def convert(b: RawBlock): FullBlock[ShallowTX] = {
    val txs = b.data.hcursor.downField("transactions").as[Seq[Json]] match {
      case Left(e) => throw new Exception(e.getMessage())
      case Right(txs) => txs.map { txJson =>
        txJson.hcursor.downField("hash").as[String] match {
          case Left(e) => throw new Exception(e.getMessage())
          case Right(hash) => ShallowTX(TXData(hash, txJson))
        }
      }
    }
    val number = java.lang.Long.parseLong(b.number.drop(2).trim(), 16)
    FullBlock(BlockData(
      b.hash,
      Long2long(number),
      b.data
    ), txs)
  }

  /**
    * Polling-loop, getting new hashes and get blocks, putting them into
    * the MVar to be consume by a BlockDispatcher.
    *
    * @param filterId filterId of subscription
    * @param ch       channel between subscription and block dispatcher
    * @return
    */
  def subscribeLoop(filterId: String, ch: MVar[Seq[FullBlock[ShallowTX]]]): Task[Unit] = for {
    hashes <- poll(filterId)
    blocks <- Task.gatherUnordered(hashes.map(underlying.getBlockWithHash(_)))
    bs <- Task.now(blocks.map(convert))
    _ <- ch.put(bs)
    ret <- subscribeLoop(filterId, ch)
  } yield ret


  /**
    * Subscribe task, it is a producer communicating via consumer
    * through MVar
    *
    * @todo fault-tolerance when subscription is lost
    * @param ch
    * @return
    */
  override def subscribeBlocks(ch: MVar[Seq[FullBlock[ShallowTX]]]): Task[Unit] = for {
    filterId <- underlying.subscribe()
    ret <- subscribeLoop(filterId, ch)
  } yield ret

  /**
    * Retrieving a block from a node
    *
    * @param height block height
    * @return
    */
  override def getBlockByHeight(height: Long): Task[FullBlock[ShallowTX]] =
    underlying.getBlockByHeight(height).map(convert)
}

/**
  * Web3 JSON-RPC connector for Ethereum clients
  *
  * @param url
  *
  */
case class Web3(url: String) extends LazyLogging {

  /**
    * Subscribing to new blocks
    *
    * @param inject  mocking HTTP response
    * @param backend HTTP backend
    * @return filterId
    */
  def subscribe(inject: Option[String] = None)
               (implicit backend: HTTPBackend): Task[String] =
    send[Unit, String](subscribeBlocks, inject)

  /**
    * Polling for new blocks
    *
    * @param filterId
    * @param inject  mocking HTTP response
    * @param backend HTTP backend
    * @return hashes of new blocks
    */
  def getBlockUpdates(filterId: String, inject: Option[String] = None)
                     (implicit backend: HTTPBackend): Task[Seq[String]] =
    send[Seq[String], Either[String, Seq[String]]](
      pollChanges(filterId), inject) map {
      case Left(h) => Seq(h)
      case Right(hashes) => hashes
    }

  /**
    * Getting TX receipt for specific hash
    *
    * @param hash
    * @param inject
    * @param backend
    * @return
    */
  def getTXReceipt(hash: String, inject: Option[String] = None)
                  (implicit backend: HTTPBackend): Task[Json] =
    send[Seq[String], Json](EthRequests.getTXReceipt(hash), inject)

  /**
    * Get block by height
    *
    * @param height
    * @param inject
    * @param backend
    * @return
    */
  def getBlockByHeight(height: Long, inject: Option[String] = None)
                      (implicit backend: HTTPBackend): Task[RawBlock] =
    send[(String, Boolean), Json](EthRequests.getBlockByHeight(height), inject).map { json =>
      val cursor = json.hcursor
      val number = cursor.downField("number").as[String]
        .getOrElse(throw new Exception("Cannot decode"))
      val hash = cursor.downField("hash").as[String]
        .getOrElse(throw new Exception("Cannot decode"))
      RawBlock(hash, number, json)
    }

  /**
    * Getting block with hash
    *
    * @param hash
    * @param inject
    * @param backend
    * @return
    */
  def getBlockWithHash(hash: String, inject: Option[String] = None)
                      (implicit backend: HTTPBackend): Task[RawBlock] =
    send[(String, Boolean), Json](getBlock(hash), inject).map { json =>
      val cursor = json.hcursor
      val number = cursor.downField("number").as[String]
        .getOrElse(throw new Exception("Cannot decode"))
      val hash = cursor.downField("hash").as[String]
        .getOrElse(throw new Exception("Cannot decode"))
      RawBlock(hash, number, json)
    }

  private def base(implicit backend: HTTPBackend) =
    sttp
      .header("Content-Type", "application/json")
      .post(uri"$url")

  private def request(body: String)
                     (implicit backend: HTTPBackend) = base
    .body(body)
    .response(asString)
    .send()
    .map(_.unsafeBody)

  private def injected(inject: Option[String], body: String)
                      (implicit backend: HTTPBackend) = inject match {
    case Some(i) => Task.now(i)
    case None => request(body)
  }

  private def handleDecodingError[T](either: Either[io.circe.Error, T]): Task[T] = {
    val onError = (error: io.circe.Error) => {
      logger.error(s"Decoding error ${error.getMessage}")
      Task.raiseError(new Exception(error.getMessage))
    }
    either.fold(onError, Task.now)
  }

  private def handleRPCError[T](either: Either[RPCError, T]) = {
    val onError = (error: RPCError) => {
      logger.error(s"RPC error: ${error.error.message}")
      Task.raiseError(new Exception(error.error.message))
    }
    either.fold(onError, Task.now)
  }

  private def send[T: Encoder, V: Decoder](req: RPCRequest[T],
                                           inject: Option[String] = None)
                                          (implicit backend: HTTPBackend) = for {
    t <- injected(inject, req.asJson.noSpaces)
    parsed <- Task.now(parse(t))
    json <- handleDecodingError(parsed)
    decoded <- Task.now(json.as[Either[RPCError, Json]])
    handledError <- handleDecodingError(decoded)
    handledRPCError <- handleRPCError(handledError)
    v <- handleDecodingError(handledRPCError.hcursor.downField("result").as[V])
  } yield v
}

object JSONDecoder {
  implicit def decodeEither[A, B](implicit decoderA: Decoder[A], decoderB: Decoder[B]):
  Decoder[Either[A, B]] = {
    c: HCursor =>
      c.as[A] match {
        case Right(a) => Right(Left(a))
        case _ => c.as[B].map(Right(_))
      }
  }
}