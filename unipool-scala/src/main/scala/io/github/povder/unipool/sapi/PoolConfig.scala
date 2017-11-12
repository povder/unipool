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

package io.github.povder.unipool.sapi

import java.util.concurrent.Executors

import io.github.povder.unipool.sapi.scheduler.{JdkScheduler, TaskScheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object PoolConfig {

  object Defaults {
    val Name: String = "unnamed"
    val Size: Int = 20
    val BorrowTimeout: Timeout = Timeout(5.seconds)
    val ValidateTimeout: Timeout = Timeout(5.seconds)
    val CreateTimeout: Timeout = Timeout(5.seconds)
    val ResetTimeout: Timeout = Timeout(5.seconds)
    val TaskScheduler: () => TaskScheduler = {
      () => new JdkScheduler(Executors.newSingleThreadScheduledExecutor())(ExecutionContext.global)
    }
    val ExecContext: ExecutionContext = ExecutionContext.global
  }

  def apply(name: String = Defaults.Name,
            size: Int = Defaults.Size,
            borrowTimeout: Timeout = Defaults.BorrowTimeout,
            validateTimeout: Timeout = Defaults.ValidateTimeout,
            createTimeout: Timeout = Defaults.CreateTimeout,
            resetTimeout: Timeout = Defaults.ResetTimeout,
            taskScheduler: () => TaskScheduler = Defaults.TaskScheduler,
            ec: ExecutionContext = Defaults.ExecContext): PoolConfig = {

    new PoolConfig(
      name = name,
      size = size,
      borrowTimeout = borrowTimeout,
      validateTimeout = validateTimeout,
      createTimeout = createTimeout,
      resetTimeout = resetTimeout,
      taskSchedulerFactory = taskScheduler,
      executionContext = ec
    )
  }

}

class PoolConfig(val name: String,
                 val size: Int,
                 val borrowTimeout: Timeout,
                 val validateTimeout: Timeout,
                 val createTimeout: Timeout,
                 val resetTimeout: Timeout,
                 val taskSchedulerFactory: () => TaskScheduler,
                 val executionContext: ExecutionContext)
