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

import java.util.concurrent.Executors

import io.github.povder.unipool._
import io.github.povder.unipool.sapi.internal.PendingRequest
import io.github.povder.unipool.sapi.internal.manager.PooledResourceManager
import io.github.povder.unipool.sapi.{Timeout, UnipoolSpec}
import io.github.povder.unipool.sapi.scheduler.{JdkScheduler, TaskScheduler}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TimeoutSchedulerSpec
  extends UnipoolSpec
    with MockFactory {

  private implicit val ec = ExecutionContext.global

  type R = AnyRef

  "TimeoutScheduler" should {

    "fail requests if processing time is exceeded" in {
      val timeoutDuration = 1.second
      val timeout = Timeout(timeoutDuration)
      val request = new PendingRequest[R](1L)
      val taskScheduler = new JdkScheduler(Executors.newSingleThreadScheduledExecutor())
      val resourceManager = mock[PooledResourceManager[R]]

      (resourceManager.evictRequestIfExists _).expects(request).returning(true)

      val timeoutScheduler = new TimeoutScheduler(resourceManager, taskScheduler)

      timeoutScheduler.scheduleTimeout(request, timeout)

      //wait for the timeout to happen
      Thread.sleep((timeoutDuration + 1.second).toMillis)

      request.resource.value shouldBe defined
      request.resource.value.foreach { connVal =>
        connVal shouldBe a[Failure[_]]
        connVal.failed.foreach { ex =>
          ex shouldBe a[TimeoutException]
        }
      }
    }

    "don't fail requests if processing time is not exceeded" in {
      val timeoutDuration = 2.seconds
      val timeout = Timeout(timeoutDuration)
      val request = new PendingRequest[R](1L)
      val taskScheduler = new JdkScheduler(Executors.newSingleThreadScheduledExecutor())
      val resourceManager = mock[PooledResourceManager[R]]
      val returnedResource = newResource()

      val timeoutScheduler = new TimeoutScheduler(resourceManager, taskScheduler)

      timeoutScheduler.scheduleTimeout(request, timeout)

      Thread.sleep((timeoutDuration - 1.second).toMillis)
      request.success(returnedResource)

      //wait for the time to reach the timeout
      Thread.sleep((timeoutDuration + 1.second).toMillis)

      //check whether the timeout scheduler didn't fail the request
      request.resource.value shouldBe defined
      request.resource.value.foreach { connVal =>
        connVal shouldBe a[Success[_]]
        connVal.foreach { returnedResource =>
          returnedResource should be theSameInstanceAs returnedResource
        }
      }
    }

    "don't schedule timeouts for infinite timeout values" in {
      val taskScheduler = mock[TaskScheduler]

      (taskScheduler.schedule(_: FiniteDuration)(_: (() => Unit))).expects(*, *).never()

      val timeoutScheduler = new TimeoutScheduler(mock[PooledResourceManager[R]], mock[TaskScheduler])
      timeoutScheduler.scheduleTimeout(mock[PendingRequest[R]], Timeout.Inf)
    }

    "timeout pending request" when {
      "request was still pending" in {
        val req = mock[PendingRequest[R]]
        val manager = mock[PooledResourceManager[R]]
        (manager.evictRequestIfExists _).expects(req).once().returning(true)
        (req.failure _).expects(where { ex: Throwable =>
          ex.isInstanceOf[TimeoutException]
        }).once().returning(())

        val scheduler = new TimeoutScheduler(manager, mock[TaskScheduler])
        scheduler.timeoutPendingReq(req, 5.seconds)
      }

      "request was not pending anymore" in {
        val req = mock[PendingRequest[R]]
        val manager = mock[PooledResourceManager[R]]
        (manager.evictRequestIfExists _).expects(req).once().returning(false)
        (req.failure _).expects(*).never()

        val scheduler = new TimeoutScheduler(manager, mock[TaskScheduler])
        scheduler.timeoutPendingReq(req, 5.seconds)
      }
    }
  }

  def newResource(): R = {
    new AnyRef
  }
}
