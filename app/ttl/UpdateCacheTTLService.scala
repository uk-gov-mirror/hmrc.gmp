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

package ttl

import javax.inject.{Inject, Singleton}
import com.mongodb.ErrorCategory
import org.bson.BsonType
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{Document, MongoCollection, MongoWriteException, ObservableFuture, SingleObservableFuture}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent

import java.util.Date
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpdateCacheTTLService @Inject() ( mongo: MongoComponent)(implicit val ec: ExecutionContext) extends Logging {

  private val collection: MongoCollection[Document] =
    mongo.database.getCollection("calculation")

  private val lockCollection: MongoCollection[Document] =
    mongo.database.getCollection("gmp-cache-locks")

  private val lockId = "update-cache-ttl-lock"

  val ttlSeconds = 10 * 60 // 10 minutes
  val ttlIndex = new IndexModel(
    Indexes.ascending("createdAt"),
    new IndexOptions()
      .name("cache-ttl")
      .expireAfter(ttlSeconds.toLong, TimeUnit.SECONDS)
  )
  lockCollection.createIndexes(Seq(ttlIndex)).toFuture()

  // Trigger at the time of Startup
  updateItem()

  private def acquireLock(): Future[Boolean] = {
    val lockDoc = Document("_id" -> lockId, "createdAt" -> new Date())
    lockCollection.insertOne(lockDoc).toFuture().map(_ => true).recover {
      case ex: MongoWriteException if ex.getError.getCategory == ErrorCategory.DUPLICATE_KEY => {
        logger.warn("Lock already exists. Skipping current job.")
        false
      }
      case ex => {
        logger.error("Unexpected error while acquiring lock", ex)
        false
      }
    }
  }

  def updateItem(): Future[Unit] =
    acquireLock().flatMap {
      case true =>
        logger.warn("Lock acquired. Starting aggregation-based update.")
        val createdATFilter = Filters.`type`("createdAt", BsonType.STRING)

        val updatePipeline = List(
          Document(
            "$set" -> Document(
              "createdAt"   -> Document("$toDate" -> "$createdAt")
            )
          )
        )

        collection
          .updateMany(
            Filters.and(
              createdATFilter
            ),
            updatePipeline
          )
          .toFuture()
          .map { result =>
            logger.warn(s"Aggregation update completed: ${result.getModifiedCount} documents updated.")
          }
          .recover { case ex =>
            logger.error("Aggregation update failed", ex)
          }

      case false =>
        Future.successful(())
    }
}
