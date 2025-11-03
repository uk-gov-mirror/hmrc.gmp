/*
 * Copyright 2025 HM Revenue & Customs
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

package cache

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class CacheRepositoryConfiguration @Inject() (configuration: Configuration) {

  lazy val cacheEnabled: Boolean = configuration
    .getOptional[Boolean](
      "cache.enabled"
    )
    .getOrElse(true)

  lazy val cacheTtl: Int = configuration
    .getOptional[Int](
      "cache.ttlInSeconds"
    )
    .getOrElse(60 * 15)

  lazy val collName: String = configuration
    .getOptional[String](
      "cache.collName"
    )
    .getOrElse("gmp-cache-locks")
}
