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
import io.github.povder.unipool.japi.{PooledResourceOps => JPooledResourceOps}
import io.github.povder.unipool.sapi.{Timeout, PooledResourceOps => SPooledResourceOps}

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

private[japi] class JavaToScalaPooledResourceOps[R](jops: JPooledResourceOps[R])
                                                   (implicit ec: ExecutionContext)
  extends SPooledResourceOps[R] {

  def validatePooledResource(resource: R, timeout: Timeout): Future[Unit] = {
    jops.validatePooledResource(resource, timeout.value.toJava).toScala.map(_ => ())
  }

  def resetPooledResource(resource: R, timeout: Timeout): Future[Unit] = {
    jops.resetPooledResource(resource, timeout.value.toJava).toScala.map(_ => ())
  }

  def destroyPooledResource(resource: R): Future[Unit] = {
    jops.destroyPooledResource(resource).toScala.map(_ => ())
  }
}
