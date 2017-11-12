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

package io.github.povder.unipool.sapi.internal.manager

import io.github.povder.unipool.sapi.UnipoolSpec
import io.github.povder.unipool.sapi.internal.{PendingReqQueue, PendingRequest}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Inside

import scala.concurrent.stm._
import scala.util.Success

class PooledResourceManagerSpec
  extends UnipoolSpec
    with MockFactory
    with Inside {

  type R = AnyRef

  "PooledResourceManage" should {
    "report resource deficit correctly" in {
      val size = 10
      val manager = new PooledResourceManager[R](size)
      val inUse = Set(createResource(), createResource())
      val creating = 2
      val idle = Set(createResource())
      atomic { implicit tx =>
        manager.inUse() = inUse
        manager.creatingCount() = creating
        manager.idle() = idle
      }

      manager.resourceDeficit() shouldBe (size - inUse.size - creating - idle.size)
    }

    "select option of idle resource" when {
      "there are no idle resources" in {
        val manager = new PooledResourceManager[R](5)
        atomic { implicit tx =>
          manager.idle() = Set()
        }
        manager.selectIdleAsInUse() shouldBe empty
      }

      "there are idle resources" in {
        val manager = new PooledResourceManager[R](5)
        val idles = Set(createResource(), createResource())
        val inUses = Set(createResource())

        atomic { implicit tx =>
          manager.idle() = idles
          manager.inUse() = inUses
        }

        val maybeSelected = manager.selectIdleAsInUse()
        maybeSelected should contain oneElementOf idles
        maybeSelected.foreach { selected =>
          atomic { implicit tx =>
            manager.idle() shouldBe (idles - selected)
            manager.inUse() shouldBe (inUses + selected)
          }
        }
      }
    }

    "attempt to decrement creaeting count" when {
      "creating count is positive" in {
        val manager = new PooledResourceManager[R](5)
        val creating = 2
        atomic { implicit tx =>
          manager.creatingCount() = creating
        }

        val newCreating = manager.decrementCreatingCount()
        newCreating shouldBe Success(creating - 1)
        atomic { implicit tx =>
          manager.creatingCount() shouldBe (creating - 1)
        }
      }

      "creating count is 0" in {
        val manager = new PooledResourceManager[R](5)
        atomic { implicit tx =>
          manager.creatingCount() = 0
        }

        the[IllegalStateException] thrownBy {
          manager.decrementCreatingCount().get
        } should have message "Creating count is not positive"
      }
    }

    "enqueue requests" when {
      "the queue is empty" in {
        val manager = new PooledResourceManager[R](5)
        val request = new PendingRequest[R](1)
        manager.enqueueRequest(request)

        inside(manager.queue.single()) { case queue: PendingReqQueue[R] =>
          queue.size shouldBe 1
          queue.contains(request) shouldBe true
        }
      }

      "the queue is non-empty" in {
        val manager = new PooledResourceManager[R](5)
        val oldRequest = new PendingRequest[R](1)
        atomic { implicit tx =>
          manager.queue() = PendingReqQueue.empty[R].enqueue(oldRequest)
        }

        val request = new PendingRequest[R](2)
        manager.enqueueRequest(request)

        inside(manager.queue.single()) { case queue: PendingReqQueue[R] =>
          queue.size shouldBe 2
          queue.contains(oldRequest) shouldBe true
          queue.contains(request) shouldBe true
        }
      }
    }

    "increase creating count only when in deficit" when {
      "deficit is more than 1" in {
        val manager = new PooledResourceManager[R](2)
        manager.increaseCreatingCountIfAtDeficit() shouldBe 2
        manager.creatingCount.single() shouldBe 1
        manager.resourceDeficit() shouldBe 1
      }

      "deficit is 1" in {
        val manager = new PooledResourceManager[R](1)
        manager.increaseCreatingCountIfAtDeficit() shouldBe 1
        manager.creatingCount.single() shouldBe 1
        manager.resourceDeficit() shouldBe 0
      }

      "deficit is 0" in {
        val manager = new PooledResourceManager[R](1)
        manager.idle.single() = Set(createResource())
        manager.increaseCreatingCountIfAtDeficit() shouldBe 0
        manager.creatingCount.single() shouldBe 0
        manager.resourceDeficit() shouldBe 0
      }
    }

    "attempt to remove active resource" when {
      "resource is in active set" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        manager.inUse.single() = Set(resource)

        manager.removeInUse(resource) shouldBe Success(())
        manager.inUse.single() shouldBe empty
      }

      "resource is not in active set" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        val anotherResource = createResource()
        manager.inUse.single() = Set(anotherResource)

        assertThrows[IllegalArgumentException] {
          manager.removeInUse(resource).get
        }

        manager.inUse.single() shouldBe Set(anotherResource)
      }
    }

    "accept returned active resources" when {
      "there are requests waiting" in {
        val manager = new PooledResourceManager[R](5)
        val request = new PendingRequest[R](1L)
        val resource = createResource()

        manager.inUse.single() = Set(resource)
        manager.queue.single() = PendingReqQueue.empty[R].enqueue(request)

        val maybeReqTry = manager.selectRequestOrAddInUseToIdle(resource)
        maybeReqTry shouldBe a[Success[_]]

        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe defined
          maybeReq.foreach { returnedReq =>
            returnedReq shouldBe theSameInstanceAs(request)
          }
        }

        inside(manager.queue.single()) { case queue: PendingReqQueue[R] =>
          queue.isEmpty shouldBe true
        }

        manager.inUse.single() should contain only resource
      }

      "there are no requests waiting" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        manager.inUse.single() = Set(resource)

        manager.selectRequestOrAddInUseToIdle(resource) shouldBe Success(None)
        manager.inUse.single() shouldBe empty
        manager.idle.single() should contain only resource
      }
    }

    "not accept returned resources with illegal argument" in {
      val manager = new PooledResourceManager[R](5)
      val inUseResource = createResource()
      val idleResource = createResource()
      manager.inUse.single() = Set(inUseResource)
      manager.idle.single() = Set(idleResource)

      assertThrows[IllegalArgumentException] {
        manager.selectRequestOrAddInUseToIdle(createResource()).get
      }
      manager.inUse.single() should contain only inUseResource
      manager.idle.single() should contain only idleResource
    }

    "clear resources" when {
      "there are resources to clear" in {
        val manager = new PooledResourceManager[R](5)

        val resources = Set(createResource(), createResource(), createResource(), createResource())

        manager.idle.single() = resources.slice(0, 2)
        manager.inUse.single() = resources.slice(2, 4)
        manager.creatingCount.single() = 2

        manager.clearResources() shouldBe resources
        manager.idle.single() shouldBe empty
        manager.inUse.single() shouldBe empty
        manager.creatingCount.single() shouldBe 0
      }

      "there are no resources" in {
        val manager = new PooledResourceManager[R](5)

        manager.clearResources() shouldBe empty
        manager.idle.single() shouldBe empty
        manager.inUse.single() shouldBe empty
        manager.creatingCount.single() shouldBe 0
      }
    }

    "evict requests" when {
      "the request is still pending" in {
        val manager = new PooledResourceManager[R](5)
        val req = new PendingRequest[R](1)
        val otherReq = new PendingRequest[R](2)

        manager.queue.single() = PendingReqQueue.empty[R].enqueue(req).enqueue(otherReq)

        manager.evictRequestIfExists(req) shouldBe true
        inside(manager.queue.single()) { case queue: PendingReqQueue[R] =>
          queue.contains(otherReq) shouldBe true
          queue.contains(req) shouldBe false
          queue.size shouldBe 1
        }
      }

      "the request is not pending anymore" in {
        val manager = new PooledResourceManager[R](5)
        val otherReq = new PendingRequest[R](2)

        manager.queue.single() = PendingReqQueue.empty[R].enqueue(otherReq)

        manager.evictRequestIfExists(new PendingRequest[R](1)) shouldBe false
        inside(manager.queue.single()) { case queue: PendingReqQueue[R] =>
          queue.contains(otherReq) shouldBe true
          queue.size shouldBe 1
        }
      }
    }

    "fail on accepting new resource" when {
      "the resource is already in idle set" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        manager.idle.single() = Set(resource)
        manager.creatingCount.single() = 1

        assertThrows[IllegalArgumentException] {
          manager.selectRequestOrAddNewToIdle(resource).get
        }
      }

      "the resource is already in active set" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        manager.inUse.single() = Set(resource)
        manager.creatingCount.single() = 1

        assertThrows[IllegalArgumentException] {
          manager.selectRequestOrAddNewToIdle(resource).get
        }
      }

      "the creating count is not positive" in {
        val manager = new PooledResourceManager[R](5)
        val resource = createResource()
        manager.creatingCount.single() = 0

        assertThrows[IllegalStateException] {
          manager.selectRequestOrAddNewToIdle(resource).get
        }
      }
    }

    "accept new resource" when {
      "there is a pending request" in {
        val manager = new PooledResourceManager[R](5)
        val request = new PendingRequest[R](1)
        manager.creatingCount.single() = 1
        manager.queue.single() = PendingReqQueue.empty[R].enqueue(request)

        val resource = createResource()
        val maybeReqTry = manager.selectRequestOrAddNewToIdle(resource)

        maybeReqTry shouldBe a[Success[_]]
        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe defined
          maybeReq.foreach { returnedReq =>
            returnedReq shouldBe theSameInstanceAs(request)
          }
        }
        manager.inUse.single() should contain only resource
        manager.idle.single() shouldBe empty
        manager.queue.single().isEmpty shouldBe true
      }

      "there are no pending requests" in {
        val manager = new PooledResourceManager[R](5)
        manager.creatingCount.single() = 1

        val resource = createResource()
        val maybeReqTry = manager.selectRequestOrAddNewToIdle(resource)

        maybeReqTry shouldBe a[Success[_]]
        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe empty
        }
        manager.inUse.single() shouldBe empty
        manager.idle.single() should contain only resource
      }
    }
  }

  def createResource(): R = {
    new R
  }

}
