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

import io.github.povder.unipool.japi.{
  Pool => JPool,
  PoolConfig => JPoolConfig,
  PooledResourceFactory => JPooledResourceFactory,
  PooledResourceOps => JPooledResourceOps
}

import io.github.povder.unipool.sapi.{Pool => SPool}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

object ScalaToJavaPool {
  def create[R](resourceFactory: JPooledResourceFactory[R],
                resourceOps: JPooledResourceOps[R],
                config: JPoolConfig): JPool[R] = {
    val scalaConfig = config.toScala
    implicit val ec = scalaConfig.executionContext

    val scalaResourceFactory = resourceFactory.toScala
    val scalaResourceOps = resourceOps.toScala

    new ScalaToJavaPool[R](SPool[R](scalaResourceFactory, scalaResourceOps, scalaConfig))
  }
}

class ScalaToJavaPool[R] protected(underlying: SPool[R])
                                  (implicit ec: ExecutionContext)
  extends ScalaToJavaPooledResourceHandler[R](underlying)
    with JPool[R] {

  def borrowResource(): CompletionStage[R] = {
    underlying.borrowResource().toJava
  }

  def borrowResource(timeout: Duration): CompletionStage[R] = {
    underlying.borrowResource(timeout.toTimeout).toJava
  }

  def shutdown(): CompletionStage[Void] = {
    underlying.shutdown().map(_ => null: Void).toJava
  }

  def isActive: Boolean = underlying.active
}
