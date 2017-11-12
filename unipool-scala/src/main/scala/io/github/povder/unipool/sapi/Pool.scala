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

package io.github.povder.unipool.sapi

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging
import io.github.povder.unipool._
import io.github.povder.unipool.sapi.internal.manager.PooledResourceManager
import io.github.povder.unipool.sapi.internal.scheduler.TimeoutScheduler
import io.github.povder.unipool.sapi.internal.Compat._
import io.github.povder.unipool.sapi.internal._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Pool {
  def apply[R](resourceFact: PooledResourceFactory[R],
               resourceOps: PooledResourceOps[R],
               config: PoolConfig): Pool[R] = {
    new DefaultPool[R](resourceFact, resourceOps, config)
  }
}

trait Pool[R] extends PooledResourceHandler[R] {

  def config: PoolConfig

  def borrowResource(): Future[R]

  def borrowResource(timeout: Timeout): Future[R]

  def shutdown(): Future[Unit]

  def active: Boolean
}

class DefaultPool[R] protected[unipool](_resourceFact: PooledResourceFactory[R],
                                        _resourceOps: PooledResourceOps[R],
                                        val config: PoolConfig)
  extends Pool[R]
    with LazyLogging {

  protected implicit val ec: ExecutionContext = config.executionContext

  private val resourceManager = new PooledResourceManager[R](config.size)
  private val taskScheduler = config.taskSchedulerFactory()
  private val timeoutScheduler = new TimeoutScheduler(resourceManager, taskScheduler)

  private val _active = new AtomicBoolean(true)

  private val resourceFact = new ExceptionWrappingPooledResourceFactory[R](_resourceFact)
  private val resourceOps = new ExceptionWrappingPooledResourceOps[R](_resourceOps)

  fillPoolIfAtDeficit()

  def borrowResource(): Future[R] = {
    borrowResource(config.borrowTimeout)
  }

  def borrowResource(timeout: Timeout): Future[R] = {
    ifActive {
      val req = new PendingRequest[R](System.nanoTime())
      resourceManager.enqueueRequest(req)

      timeoutScheduler.scheduleTimeout(req, timeout)
      useAtMostOneIdle()

      req.resource
    }
  }

  def shutdown(): Future[Unit] = {
    val doShutdown = _active.compareAndSet(true, false)
    if (doShutdown) {
      logger.info(s"Shutting down '${config.name}' pool")
      val resources = resourceManager.clearResources()

      foldShutdownFutures(
        resources.map(resourceOps.destroyPooledResource) +
          resourceFact.shutdown() +
          taskScheduler.shutdown()
      )
    } else {
      logger.warn(s"Pool '${config.name}' was already shut down")
      Future.unit
    }
  }

  def active: Boolean = _active.get()

  def returnResource(resource: R): Future[Unit] = {
    ifActive {
      resourceOps.resetPooledResource(resource, config.resetTimeout)
        .flatMap(_ => resourceOps.validatePooledResource(resource, config.validateTimeout))
        .flatMap { _ =>
          Future.fromTry(resourceManager.selectRequestOrAddInUseToIdle(resource))
            .map { maybePendingReq =>
              maybePendingReq.foreach(_.success(resource))
            }
        }
        .recoverWith(handleResourceReturnErrors(resource))
    }
  }

  def destroyResource(resource: R): Future[Unit] = {
    ifActive {
      Future.fromTry(resourceManager.removeInUse(resource)).flatMap { _ =>
        resourceOps.destroyPooledResource(resource)
          .recover {
            case NonFatal(ex) =>
              logWarnException(s"Pool '${config.name}' could not destroy a resource", ex)
          }
          .andThen { case _ =>
            fillPoolIfAtDeficit()
          }
      }
    }
  }

  private def maybeValidIdle(): Future[Option[R]] = {
    val maybeIdle = resourceManager.selectIdleAsInUse()

    maybeIdle match {
      case Some(resource) =>
        resourceOps.validatePooledResource(resource, config.validateTimeout)
          .map(_ => Some(resource))
          .recoverWith {
            case ex: PooledResourceValidationException =>
              logWarnException(s"Validation of idle resource failed in pool '${config.name}'", ex)
              destroyResource(resource).transformWith(_ => maybeValidIdle())
          }

      case None => Future.successful(None)
    }
  }

  private def useAtMostOneIdle(): Unit = {
    maybeValidIdle().foreach { maybeResource =>
      maybeResource.foreach { conn =>
        resourceManager.selectRequestOrAddInUseToIdle(conn).map { maybeReq =>
          maybeReq.foreach(_.success(conn))
        }
      }
    }
  }

  private def acceptNewResource(resource: R): Try[Unit] = {
    logger.debug(s"Pool '${config.name}' successfully created a new resource")
    resourceManager.selectRequestOrAddNewToIdle(resource).map { maybePendingReq =>
      maybePendingReq.foreach(_.success(resource))
    }
  }

  private def createNewResourceIfAtDeficit(): Future[Option[R]] = {
    val deficit = resourceManager.increaseCreatingCountIfAtDeficit()
    if (deficit > 0) {
      resourceFact.createResource(this, config.createTimeout)
        .flatMap { resource =>
          resourceOps.validatePooledResource(resource, config.validateTimeout)
            .map(_ => Some(resource))
        }
        .transformWith {
          case Success(Some(resource)) =>
            Future.fromTry(acceptNewResource(resource)).map { _ =>
              Some(resource)
            }

          case Failure(NonFatal(ex)) =>
            logWarnException(s"Pool '${config.name}' could not create a new resource", ex)
            resourceManager.decrementCreatingCount() match {
              case Success(_) => Future.failed(ex)
              case Failure(NonFatal(decrementEx)) =>
                val internalError = new InternalErrorException(
                  "Attempted to decrement creating count but it was already non-positive",
                  Some(decrementEx)
                )
                logger.error(internalError.getMessage, internalError)
                Future.failed(internalError)
            }
        }
    } else {
      Future.successful(None)
    }
  }

  private def handleResourceReturnErrors(resource: R): PartialFunction[Throwable, Future[Unit]] = {
    case ex: PooledResourceValidationException =>
      logWarnException(s"Validation of returned resource '$resource' failed in pool '${config.name}'", ex)
      destroyResource(resource).recover {
        case destroyEx =>
          logWarnException(s"Destroying resource '$resource' failed in pool '${config.name}'", destroyEx)
          ()
      }

    case ex: PooledResourceResetException =>
      logWarnException(s"Reset of returned resource '$resource' failed in pool '${config.name}'", ex)
      destroyResource(resource).recover {
        case destroyEx =>
          logWarnException(s"Destroying resource '$resource' failed in pool '${config.name}'", destroyEx)
          ()
      }

    case NonFatal(ex) =>
      logWarnException(s"Error occurred when accepting returned resource '$resource' in pool '${config.name}", ex)
      destroyResource(resource).recover {
        case destroyEx =>
          logWarnException(s"Destroying resource '$resource' failed in pool '${config.name}'", destroyEx)
          throw ex
      }
  }

  private def fillPoolIfAtDeficit(): Unit = {
    val deficit = resourceDeficit()
    if (deficit > 0) {
      (1 to deficit).foreach { _ =>
        createNewResourceIfAtDeficit()
      }
    }
  }

  private def resourceDeficit(): Int = {
    if (!_active.get()) {
      0
    } else {
      resourceManager.resourceDeficit()
    }
  }

  private def foldShutdownFutures(futures: Set[Future[Unit]]): Future[Unit] = {
    futures.foldLeft(Future.unit) { (acc, current) =>
      acc.flatMap { _ =>
        current.recover {
          case ex =>
            logWarnException(s"Error occurred during pool '${config.name}' shutdown", ex)
            ()
        }
      }
    }
  }

  private def ifActive[A](body: => Future[A]): Future[A] = {
    if (_active.get()) {
      body
    } else {
      Future.failed(new PoolInactiveException(s"Pool '${config.name}' is shut down"))
    }
  }

  private def logWarnException(msg: String, ex: Throwable): Unit = {
    if (logger.underlying.isDebugEnabled) {
      logger.warn(msg, ex)
    } else {
      logger.warn(s"$msg: ${ex.getMessage}")
    }
  }
}
