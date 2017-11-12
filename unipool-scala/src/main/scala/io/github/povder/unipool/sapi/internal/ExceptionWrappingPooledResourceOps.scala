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

import io.github.povder.unipool.sapi.{PooledResourceOps, Timeout}
import io.github.povder.unipool._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

private[unipool]
class ExceptionWrappingPooledResourceOps[R](underlying: PooledResourceOps[R])
                                           (implicit ec: ExecutionContext)
  extends PooledResourceOps[R] {

  def validatePooledResource(resource: R, timeout: Timeout): Future[Unit] = {
    underlying.validatePooledResource(resource, timeout)
      .recoverWith {
        case ex: PooledResourceValidationException => Future.failed(ex)
        case NonFatal(ex) => Future.failed(new PooledResourceValidationException(ex.getMessage, ex))
      }
  }

  def resetPooledResource(resource: R, timeout: Timeout): Future[Unit] = {
    underlying.resetPooledResource(resource, timeout)
      .recoverWith {
        case ex: PooledResourceResetException => Future.failed(ex)
        case NonFatal(ex) => Future.failed(new PooledResourceResetException(ex.getMessage, ex))
      }
  }

  def destroyPooledResource(resource: R): Future[Unit] = {
    underlying.destroyPooledResource(resource)
      .recoverWith {
        case ex: PooledResourceDestroyException => Future.failed(ex)
        case NonFatal(ex) => Future.failed(new PooledResourceDestroyException(ex.getMessage, ex))
      }
  }
}
