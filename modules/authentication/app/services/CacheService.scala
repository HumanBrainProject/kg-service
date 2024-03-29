/*
 * Copyright 2018 - 2021 Swiss Federal Institute of Technology Lausanne (EPFL)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This open source software code was developed in part or in whole in the
 * Human Brain Project, funded from the European Union's Horizon 2020
 * Framework Programme for Research and Innovation under
 * Specific Grant Agreements No. 720270, No. 785907, and No. 945539
 * (Human Brain Project SGA1, SGA2 and SGA3).
 *
 */

package services

import akka.Done
import cats.syntax.option._
import monix.eval.Task
import play.api.Logger
import play.api.cache.AsyncCacheApi

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

trait CacheService {

  val log = Logger(this.getClass)

  def getOrElse[A: ClassTag](cache: AsyncCacheApi, key: String)(
    orElse: => Task[Option[A]]
  ): Task[Option[A]] = {
    get[A](cache, key).flatMap {
      case Some(elem) =>
        log.debug(s"Cache element found in cache")
        Task.pure(elem.some)
      case None =>
        log.debug("Cache element not found executing orElse")
        orElse
    }
  }

  def get[A: ClassTag](cache: AsyncCacheApi, key: String): Task[Option[A]] = Task.deferFuture(cache.get[A](key))

  def clearCache(cache: AsyncCacheApi): Task[Done] = Task.deferFuture(cache.removeAll())

  def set[A: ClassTag](cache: AsyncCacheApi, key: String, value: A, ttl: Duration = Duration.Inf): Task[Done] = {
    Task.deferFuture(cache.set(key, value, ttl))
  }
}
