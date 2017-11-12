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

import scala.collection.immutable.TreeSet

private[unipool]
sealed trait PendingReqQueue[R] {
  def contains(req: PendingRequest[R]): Boolean

  def evict(req: PendingRequest[R]): PendingReqQueue[R]

  def enqueue(req: PendingRequest[R]): PendingReqQueue[R]

  def dequeueOption: Option[(PendingRequest[R], PendingReqQueue[R])]

  def size: Int

  def isEmpty: Boolean
}

private[unipool] object PendingReqQueue {
  def empty[R]: PendingReqQueue[R] = new Empty

  private class Empty[R] extends PendingReqQueue[R] {
    def contains(req: PendingRequest[R]): Boolean = false

    def evict(req: PendingRequest[R]): PendingReqQueue[R] = this

    def enqueue(req: PendingRequest[R]): PendingReqQueue[R] = {
      new NonEmpty(TreeSet(req)(Ordering.by(_.ordNumber)))
    }

    val dequeueOption: Option[(PendingRequest[R], PendingReqQueue[R])] = None

    val isEmpty = true

    val size = 0

    override val toString: String = "PendingReqQueue()"
  }

  private class NonEmpty[R](private val set: TreeSet[PendingRequest[R]])
    extends PendingReqQueue[R] {

    def contains(req: PendingRequest[R]): Boolean = {
      set.contains(req)
    }

    def evict(req: PendingRequest[R]): PendingReqQueue[R] = {
      val newSet = set - req
      if (newSet.isEmpty) {
        PendingReqQueue.empty
      } else {
        new NonEmpty(newSet)
      }
    }

    def enqueue(req: PendingRequest[R]): PendingReqQueue[R] = {
      new NonEmpty(set + req)
    }

    def dequeueOption: Option[(PendingRequest[R], PendingReqQueue[R])] = {
      set.headOption.map { req =>
        val newSet = set - req
        val newQueue = if (newSet.isEmpty) {
          PendingReqQueue.empty[R]
        } else {
          new NonEmpty(newSet)
        }
        (req, newQueue)
      }
    }

    val isEmpty = false

    val size: Int = set.size

    override lazy val toString: String = {
      set.mkString("PendingReqQueue(", ",", ")")
    }
  }

}
