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

import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit.NANOSECONDS

import io.github.povder.unipool.japi.scheduler.{TaskSchedulerFactory, ScheduledTask => JScheduledTask}

import io.github.povder.unipool.japi.{
  PoolConfig => JPoolConfig,
  PooledResourceFactory => JPooledResourceFactory,
  PooledResourceHandler => JPooledResourceHandler,
  PooledResourceOps => JPooledResourceOps
}

import io.github.povder.unipool.sapi.scheduler.{
  ScheduledTask => SScheduledTask,
  TaskScheduler => STaskScheduler
}

import io.github.povder.unipool.sapi.{
  Timeout,
  PoolConfig => SPoolConfig,
  PooledResourceFactory => SPooledResourceFactory,
  PooledResourceHandler => SPooledResourceHandler,
  PooledResourceOps => SPooledResourceOps
}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, Duration => SDuration}

private[japi] object Conversions {

  implicit class ScalaToJavaDuration(val duration: SDuration) extends AnyVal {
    def toJava: JDuration = {
      duration match {
        case SDuration.Inf => JDuration.ofSeconds(Long.MaxValue)
        case other: SDuration => JDuration.ofNanos(other.toNanos)
      }
    }
  }

  implicit class JavaDurationToScala(val duration: java.time.Duration)
    extends AnyVal {

    def toScala: FiniteDuration = {
      val nanos = try {
        duration.toNanos
      } catch {
        case _: ArithmeticException => Long.MaxValue
      }
      //The cast below is for Scala 2.11
      FiniteDuration(nanos, NANOSECONDS).toCoarsest.asInstanceOf[FiniteDuration]
    }
  }

  implicit class JavaDurationToTimeout(val duration: java.time.Duration)
    extends AnyVal {

    def toTimeout: Timeout = Timeout(duration.toScala)
  }

  implicit class JavaToScalaTaskSchedulerFactory(val factory: TaskSchedulerFactory)
    extends AnyVal {

    def toScala(implicit ec: ExecutionContext): () => STaskScheduler = {
      () => {
        new JavaToScalaTaskScheduler(factory.createTaskScheduler())
      }
    }
  }

  implicit class JavaConfigToScala(val config: JPoolConfig) extends AnyVal {
    def toScala: SPoolConfig = {
      val ec: ExecutionContext = config.executor() match {
        case ec: ExecutionContext => ec
        case other => ExecutionContext.fromExecutor(other)
      }

      SPoolConfig(
        name = config.name(),
        size = config.size(),
        borrowTimeout = config.borrowTimeout().toTimeout,
        validateTimeout = config.validateTimeout().toTimeout,
        createTimeout = config.createTimeout().toTimeout,
        resetTimeout = config.resetTimeout().toTimeout,
        taskScheduler = config.taskSchedulerFactory().toScala(ec),
        ec = ec
      )
    }
  }

  implicit class JavaToScalaPooledResourceFactoryConv[R](val jfact: JPooledResourceFactory[R])
    extends AnyVal {

    def toScala(implicit ec: ExecutionContext): SPooledResourceFactory[R] = {
      new JavaToScalaPooledResourceFactory[R](jfact)
    }

  }

  implicit class JavaToScalaPooledResourceOpsConv[R](val jops: JPooledResourceOps[R])
    extends AnyVal {

    def toScala(implicit ec: ExecutionContext): SPooledResourceOps[R] = {
      new JavaToScalaPooledResourceOps[R](jops)
    }
  }

  implicit class ScalaToJavaPooledResourceHandlerConv[R](val shandler: SPooledResourceHandler[R])
    extends AnyVal {

    def toJava(implicit ec: ExecutionContext): JPooledResourceHandler[R] = {
      new ScalaToJavaPooledResourceHandler[R](shandler)
    }
  }

  implicit class JavaToScalaScheduledTaskConv[R](val jScheduledTask: JScheduledTask)
    extends AnyVal {

    def toScala: SScheduledTask = {
      new JavaToScalaScheduledTask(jScheduledTask)
    }
  }

  implicit class ScalaToJavaScheduledTaskConv[R](val sScheduledTask: SScheduledTask)
    extends AnyVal {

    def toJava: JScheduledTask = {
      new ScalaToJavaScheduledTask(sScheduledTask)
    }
  }

}
