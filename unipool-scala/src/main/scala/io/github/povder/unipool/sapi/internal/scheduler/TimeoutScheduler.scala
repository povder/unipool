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

package io.github.povder.unipool.sapi.internal.scheduler

import com.typesafe.scalalogging.LazyLogging
import io.github.povder.unipool.TimeoutException
import io.github.povder.unipool.sapi.Timeout
import io.github.povder.unipool.sapi.internal.PendingRequest
import io.github.povder.unipool.sapi.internal.manager.PooledResourceManager
import io.github.povder.unipool.sapi.scheduler.TaskScheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

private[unipool]
class TimeoutScheduler[R](poolManager: PooledResourceManager[R],
                          taskScheduler: TaskScheduler)
                         (implicit ec: ExecutionContext)
  extends LazyLogging {

  def scheduleTimeout(req: PendingRequest[R], timeout: Timeout): Unit = {
    if (timeout.value.isFinite()) {
      val finiteTimeout = FiniteDuration(timeout.value.length, timeout.value.unit)
      val task = taskScheduler.schedule(finiteTimeout) { () =>
        timeoutPendingReq(req, finiteTimeout)
      }
      req.resource.onComplete { _ =>
        task.cancel()
      }
    }
  }

  private[scheduler]
  def timeoutPendingReq(req: PendingRequest[R], timeout: FiniteDuration): Unit = {
    val existed = poolManager.evictRequestIfExists(req)
    if (existed) {
      logger.debug(s"Failing borrow request '$req' because of a timeout after $timeout")
      req.failure(new TimeoutException(Timeout(timeout)))
    }
  }

}
