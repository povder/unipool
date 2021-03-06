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

import java.util.concurrent.CompletionStage

import io.github.povder.unipool.japi.{PooledResourceHandler => JPooledResourceHandler}
import io.github.povder.unipool.sapi.{PooledResourceHandler => SPooledResourceHandler}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

private[japi] class ScalaToJavaPooledResourceHandler[R](underlying: SPooledResourceHandler[R])
                                                       (implicit ec: ExecutionContext)
  extends JPooledResourceHandler[R] {

  def returnResource(resource: R): CompletionStage[Void] = {
    underlying.returnResource(resource).map(_ => null: Void).toJava
  }

  def destroyResource(resource: R): CompletionStage[Void] = {
    underlying.destroyResource(resource).map(_ => null: Void).toJava
  }
}
