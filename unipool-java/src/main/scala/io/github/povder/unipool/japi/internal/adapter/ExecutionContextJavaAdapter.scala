/*
 * Copyright 2017 Krzysztof Pado
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.povder.unipool.japi.internal.adapter

import java.util.concurrent.{AbstractExecutorService, TimeUnit}
import java.util.{Collections => JCollections, List => JList}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

private[japi] class ExecutionContextJavaAdapter(ec: ExecutionContext)
  extends AbstractExecutorService
    with ExecutionContextExecutorService {

  val isTerminated: Boolean = false

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false

  def shutdownNow(): JList[Runnable] = JCollections.emptyList()

  def shutdown(): Unit = ()

  val isShutdown: Boolean = false

  def execute(runnable: Runnable): Unit = ec.execute(runnable)

  def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}
