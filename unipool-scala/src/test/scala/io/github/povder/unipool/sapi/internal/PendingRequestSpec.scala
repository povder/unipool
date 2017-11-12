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

import io.github.povder.unipool.sapi.UnipoolSpec
import org.scalamock.scalatest.MockFactory

import scala.concurrent.ExecutionContext

class PendingRequestSpec
  extends UnipoolSpec
    with MockFactory {

  implicit private val ec = ExecutionContext.global

  type R = AnyRef

  "A pending request" should {
    "Complete resource future successfully" in {
      val req = new PendingRequest[R](1L)
      val resource = new R()
      req.resource.isCompleted shouldBe false
      req.success(resource)
      req.resource.isCompleted shouldBe true
      req.resource.foreach { futureConn =>
        futureConn should be theSameInstanceAs resource
      }
    }

    "Complete resource future with a failure" in {
      val req = new PendingRequest[R](1L)
      val ex = new RuntimeException
      req.resource.isCompleted shouldBe false
      req.failure(ex)
      req.resource.isCompleted shouldBe true
      req.resource.failed.foreach { futureEx =>
        futureEx should be theSameInstanceAs ex
      }
    }
  }

}
