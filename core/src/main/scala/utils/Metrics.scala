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

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Gauge, MetricFilter, MetricRegistry}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}

/**
  * Since most of the code is functional, we do a small hack here.
  *
  * @param name
  * @param graphite
  */
class Metrics(name: String, graphite: Option[String]) {
  val registry = new MetricRegistry
  graphite.foreach { url =>
    val graphite = new Graphite(new InetSocketAddress(url, 2003))
    val reporter = GraphiteReporter.forRegistry(registry)
      .prefixedWith(s"$name")
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)
    reporter.start(1, TimeUnit.SECONDS);
  }

  var blockOffset : Long = -1
  var failedReceiptLast: Int = -1
  var failedSink: Int = -1
  var successLast: Int = -1

  registry.register("txs-failed-receipt", new Gauge[Int] {
    override def getValue: Int = failedReceiptLast
  })
  registry.register("txs-failed-sink", new Gauge[Int] {
    override def getValue: Int = failedSink
  })
  registry.register("txs-success", new Gauge[Int] {
    override def getValue: Int = successLast
  })
  registry.register("block-offset", new Gauge[Long] {
    override def getValue: Long = blockOffset
  })
}
