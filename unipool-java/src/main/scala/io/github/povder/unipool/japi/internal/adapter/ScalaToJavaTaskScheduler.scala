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
import java.util.concurrent.CompletionStage

import Conversions._

import io.github.povder.unipool.japi.scheduler.{
  ScheduledTask => JScheduledTask,
  TaskScheduler => JTaskScheduler
}

import io.github.povder.unipool.sapi.scheduler.{TaskScheduler => STaskScheduler}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

private[japi] class ScalaToJavaTaskScheduler(underlying: STaskScheduler)
                                            (implicit ec: ExecutionContext)
  extends JTaskScheduler {

  def schedule(delay: Duration, action: Runnable): JScheduledTask = {
    val scalaDelay = delay.toScala

    underlying.schedule(scalaDelay) { () =>
      action.run()
    }.toJava

  }

  def shutdown(): CompletionStage[Void] = {
    underlying.shutdown().map(_ => null: Void).toJava
  }
}
