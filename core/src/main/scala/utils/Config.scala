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
package com.reebo.ethsync.core.utils

import com.typesafe.scalalogging.LazyLogging

import scala.language.implicitConversions

/**
  * Configuration for a setup
  *
  * @param network network name (could be an identifier)
  * @param nodes   sequence of URI for Ethereum nodes (http://....)
  * @param kafka   sequence of Kafka broker hosts
  * @param topic   topic in Kafka for full transactions
  */
case class Config(network: String,
                  nodes: Seq[String],
                  kafka: Seq[String],
                  topic: String)

object Config extends LazyLogging {

  /**
    * Load configs from ENV
    *
    * @return sequence of configs of ENV
    */
  def load: Seq[Config] = {
    val networks = Seq("mainnet", "kovan", "rinkeby", "ropsten")
      .filter { n =>
        sys.env.isDefinedAt(s"${n.toUpperCase()}_NODES") &&
          sys.env.isDefinedAt(s"${n.toUpperCase()}_TOPIC")
      }

    networks
      .map { n =>
        (n,
          getStrings(n, "NODES"),
          getStrings(n, "BROKERS"),
          getString(n, "TOPIC"))
      }
      .map { case (n, nodes, brokers, topic) =>
        logger.info(s"Loaded setup for ${n} with ${n.size} nodes, ${brokers.size} brokers.")
        Config(n, nodes, brokers, topic)
      }
  }

  def getStrings(name: String, suffix: String): Array[String] = {
    sys.env.getOrElse(s"${name.toUpperCase()}_$suffix",
      throw new Exception(
        s"${name}_$suffix not found")
    ).split(",")
  }

  def getString(name: String, suffix: String): String = {
    sys.env.getOrElse(s"${name.toUpperCase()}_$suffix", throw new Exception(
      s"${name}_$suffix not found"
    ))
  }

}