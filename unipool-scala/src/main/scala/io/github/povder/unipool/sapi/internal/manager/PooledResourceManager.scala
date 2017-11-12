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

import com.typesafe.scalalogging.LazyLogging
import io.github.povder.unipool.sapi.internal.{PendingReqQueue, PendingRequest}

import scala.concurrent.stm.{InTxn, Ref, Txn, atomic}
import scala.util.{Failure, Success, Try}

private[unipool] class PooledResourceManager[R](poolSize: Int)
  extends LazyLogging {

  private[manager] val queue = Ref(PendingReqQueue.empty[R])
  private[manager] val idle = Ref(Set.empty[R])
  private[manager] val inUse = Ref(Set.empty[R])
  private[manager] val creatingCount = Ref(0)

  def removeInUse(resource: R): Try[Unit] = {
    atomic { implicit tx =>
      if (inUse().contains(resource)) {
        removeInUseInternal(resource)
        Success(())
      } else {
        Failure(new IllegalArgumentException(s"Resource '$resource' is not in use"))
      }
    }
  }

  def selectRequestOrAddInUseToIdle(resource: R): Try[Option[PendingRequest[R]]] = {
    atomic { implicit tx =>
      if (inUse().contains(resource)) {
        Success(
          dequeuePendingRequest() match {
            case s@Some(_) => s
            case None =>
              removeInUseInternal(resource)
              addIdleInternal(resource)
              None
          }
        )
      } else {
        Failure(new IllegalArgumentException(s"Resource '$resource' is not in use"))
      }
    }
  }

  def selectRequestOrAddNewToIdle(resource: R): Try[Option[PendingRequest[R]]] = {
    atomic { implicit tx =>
      if (idle().contains(resource) || inUse().contains(resource)) {
        Failure(new IllegalArgumentException(s"Resource '$resource' is already managed"))
      } else {
        decrementCreatingCount().map { _ =>
          dequeuePendingRequest() match {
            case Some(req) =>
              addInUseInternal(resource)
              Some(req)

            case None =>
              addIdleInternal(resource)
              None
          }
        }
      }
    }
  }

  def increaseCreatingCountIfAtDeficit(): Int = {
    atomic { implicit tx =>
      val deficit = resourceDeficit()
      if (deficit > 0) {
        incrementCreatingCount()
      }
      deficit
    }
  }

  def decrementCreatingCount(): Try[Int] = {
    atomic { implicit tx =>
      if (creatingCount() > 0) {
        creatingCount() = creatingCount() - 1
        Success(creatingCount())
      } else Failure(new IllegalStateException("Creating count is not positive"))
    }
  }

  def evictRequestIfExists(req: PendingRequest[R]): Boolean = {
    atomic { implicit tx =>
      if (queue().contains(req)) {
        queue() = queue().evict(req)
        true
      } else false
    }
  }

  def enqueueRequest(req: PendingRequest[R]): Unit = {
    atomic { implicit tx =>
      queue() = queue().enqueue(req)
    }
  }

  def clearResources(): Set[R] = {
    atomic { implicit tx =>
      val resources = inUse() ++ idle()
      inUse() = Set.empty
      idle() = Set.empty
      creatingCount() = 0
      resources
    }
  }

  def selectIdleAsInUse(): Option[R] = {
    atomic { implicit tx =>
      val maybeResource = idle().headOption
      maybeResource.foreach { resource =>
        removeIdleInternal(resource)
        addInUseInternal(resource)
      }
      maybeResource
    }
  }

  def resourceDeficit(): Int = {
    atomic { implicit tx =>
      poolSize - creatingCount() - idle().size - inUse().size
    }
  }

  private def dequeuePendingRequest()(implicit txn: InTxn): Option[PendingRequest[R]] = {
    queue().dequeueOption.map { case (head, tail) =>
      queue() = tail
      head
    }
  }

  private def addResourceToSet(resource: R,
                               coll: Ref[Set[R]],
                               msg: String)
                              (implicit tx: InTxn): Unit = {
    coll() = coll() + resource
    Txn.afterCommit { _ =>
      logger.debug(s"$resource $msg")
    }
  }

  private def removeResourceFromSet(resource: R,
                                    coll: Ref[Set[R]],
                                    msg: String)
                                   (implicit tx: InTxn): Unit = {
    coll() = coll() - resource
    Txn.afterCommit { _ =>
      logger.debug(s"$resource $msg")
    }
  }

  private def addIdleInternal(resource: R)(implicit tx: InTxn): Unit = {
    addResourceToSet(resource, idle, "added to the idle set")
  }

  private def removeIdleInternal(resource: R)(implicit tx: InTxn): Unit = {
    removeResourceFromSet(resource, idle, "removed from the idle set")
  }

  private def addInUseInternal(resource: R)(implicit tx: InTxn): Unit = {
    addResourceToSet(resource, inUse, "added to the in-use set")
  }

  private def removeInUseInternal(resource: R)(implicit tx: InTxn): Unit = {
    removeResourceFromSet(resource, inUse, "removed from the in-use set")
  }

  private def incrementCreatingCount()(implicit tx: InTxn): Int = {
    creatingCount() = creatingCount() + 1
    creatingCount()
  }
}
