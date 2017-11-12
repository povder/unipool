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

import java.time.Duration
import java.util.concurrent.ExecutorService

import io.github.povder.unipool.sapi.PoolConfig.Defaults
import Conversions._
import io.github.povder.unipool.japi.scheduler.{TaskScheduler, TaskSchedulerFactory}

private[japi] object JavaConfigDefaults {
  val Name: String = Defaults.Name

  val Size: Integer = Defaults.Size

  val BorrowTimeout: Duration = Defaults.BorrowTimeout.value.toJava

  val ValidateTimeout: Duration = Defaults.ValidateTimeout.value.toJava

  val CreateTimeout: Duration = Defaults.CreateTimeout.value.toJava

  val ResetTimeout: Duration = Defaults.ResetTimeout.value.toJava

  val TaskSchedulerFactory: TaskSchedulerFactory = {
    new TaskSchedulerFactory {
      def createTaskScheduler(): TaskScheduler = {
        new ScalaToJavaTaskScheduler(Defaults.TaskScheduler())(Defaults.ExecContext)
      }
    }
  }

  val ExecService: ExecutorService = {
    new ExecutionContextJavaAdapter(Defaults.ExecContext)
  }
}
