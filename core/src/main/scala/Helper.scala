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

import com.typesafe.scalalogging.Logger
import monix.eval.{Coeval, Task}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object CirceHelpers {

  def handleDecodingErrorTry[T](logger: Logger, either: Either[io.circe.Error, T]): Try[T] = {
    val onError = (error: io.circe.Error) => {
      logger.error(s"Decoding error ${error.getMessage}")
      Failure(new Exception(error.getMessage))
    }
    either.fold(onError, Success(_))
  }

  def handleDecodingError[T](logger: Logger, either: Either[io.circe.Error, T]): Task[T] = {
    val onError = (error: io.circe.Error) => {
      logger.error(s"Decoding error ${error.getMessage}")
      Task.raiseError(new Exception(error.getMessage))
    }
    either.fold(onError, Task.now)
  }
}

/**
  * Exponential backoff retry filter
  *
  * @param retries
  * @param delay
  */
case class BackoffRetry(retries: Int, delay: FiniteDuration)
  extends RetryFilter {

  def retry[A](logger: Logger, source: Task[A]): Task[A] =
    retryBackoff(logger, source)

  def retryBackoff[A](logger: Logger, source: Task[A],
                      retries: Int = retries,
                      delay: FiniteDuration = delay): Task[A] = {
    source.onErrorHandleWith {
      case ex: Exception =>
        if (retries > 0)
          retryBackoff(logger, source, retries - 1, delay * 2)
            .delayExecution(delay)
        else {
          logger.error(s"SEVERE: ${
            ex.getMessage
          }", ex)
          Task.raiseError(ex)
        }
    }
  }
}