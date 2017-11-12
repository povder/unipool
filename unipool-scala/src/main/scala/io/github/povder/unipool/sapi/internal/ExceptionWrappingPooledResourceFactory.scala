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

package io.github.povder.unipool.sapi.internal

import io.github.povder.unipool.sapi.{PooledResourceFactory, PooledResourceHandler, Timeout}
import io.github.povder.unipool.{PooledResourceCreateException, PooledResourceFactoryShutdownException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

private[unipool]
class ExceptionWrappingPooledResourceFactory[R](underlying: PooledResourceFactory[R])
                                               (implicit ec: ExecutionContext)
  extends PooledResourceFactory[R] {

  def createResource(pool: PooledResourceHandler[R], timeout: Timeout): Future[R] = {
    underlying.createResource(pool, timeout)
      .recoverWith {
        case ex: PooledResourceCreateException => Future.failed(ex)
        case NonFatal(ex) => Future.failed(new PooledResourceCreateException(ex.getMessage, ex))
      }
  }

  def shutdown(): Future[Unit] = {
    underlying.shutdown()
      .recoverWith {
        case ex: PooledResourceFactoryShutdownException => Future.failed(ex)
        case NonFatal(ex) => Future.failed(new PooledResourceFactoryShutdownException(ex.getMessage, ex))
      }
  }
}
