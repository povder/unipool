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

import Conversions._
import io.github.povder.unipool.japi.scheduler.{TaskScheduler => JTaskScheduler}
import io.github.povder.unipool.sapi.scheduler.{ScheduledTask => SScheduledTask, TaskScheduler => STaskScheduler}

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

private[japi] class JavaToScalaTaskScheduler(javaScheduler: JTaskScheduler)
                                            (implicit ec: ExecutionContext)
  extends STaskScheduler {

  def schedule(delay: FiniteDuration)(action: () => Unit): SScheduledTask = {
    val javaDuration = delay.toJava
    javaScheduler.schedule(javaDuration, new Runnable {
      def run(): Unit = action()
    }).toScala
  }

  def shutdown(): Future[Unit] = {
    javaScheduler.shutdown().toScala.map(_ => ())
  }
}
