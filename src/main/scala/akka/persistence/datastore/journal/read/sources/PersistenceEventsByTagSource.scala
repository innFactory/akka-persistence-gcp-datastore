/*
 * Copyright 2020 innFactory GmbH | innfactory.de
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

package akka.persistence.datastore.journal.read.sources

import akka.actor.ExtendedActorSystem
import akka.persistence.datastore.DatastoreCommon
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.serialization.{ DatastoreSerializer, SerializedPayload }
import akka.persistence.query.{ EventEnvelope, Offset }
import akka.stream.{ ActorAttributes, Attributes, Outlet, SourceShape }
import akka.stream.stage.{ GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic }
import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{ CompositeFilter, OrderBy, PropertyFilter }

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class PersistenceEventsByTagSource(
  tag: String,
  timestamp: Long,
  refreshInterval: FiniteDuration,
  system: ExtendedActorSystem
) extends GraphStage[SourceShape[EventEnvelope]] {

  private val datastoreSerializer = new DatastoreSerializer(system)

  private case object Continue

  val out: Outlet[EventEnvelope] = Outlet("PersistenceEventsByTagSource.out")

  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  private val sequenceNrKey    = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val payloadKey       = DatastoreCommon.payloadKey
  private val kind             = DatastoreCommon.journalKind
  private val tagsKey          = DatastoreCommon.tagsKey
  private val timestampKey     = DatastoreCommon.timestampKey
  private val serializerKey    = DatastoreCommon.serializerKey
  private val manifestKey      = DatastoreCommon.manifestKey

  override protected def initialAttributes: Attributes = Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      private val Limit            = 1000
      private var currentTimestamp = timestamp
      private var buf              = Vector.empty[EventEnvelope]

      override def preStart(): Unit =
        scheduleWithFixedDelay(Continue, refreshInterval, refreshInterval)

      override def onPull(): Unit = {
        query()
        tryPush()
      }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            query()
            tryPush()
          }
        }
      )

      override def onDownstreamFinish(cause: Throwable): Unit = {
        // close connection if responsible for doing so
      }

      private def query(): Unit =
        if (buf.isEmpty)
          try buf = Select.run(tag, currentTimestamp, Limit)
          catch {
            case NonFatal(e) =>
              failStage(e)
          }

      private def tryPush(): Unit =
        if (buf.nonEmpty && isAvailable(out)) {
          push(out, buf.head)
          buf = buf.tail
        }

      override protected def onTimer(timerKey: Any): Unit =
        timerKey match {
          case Continue =>
            query()
            tryPush()
        }

      object Select {
        def run(tag: String, from: Long, limit: Int): Vector[EventEnvelope] = {
          val query: StructuredQuery[Entity] =
            Query
              .newEntityQueryBuilder()
              .setKind(kind)
              .setFilter(
                CompositeFilter.and(
                  PropertyFilter.eq(tagsKey, tag),
                  PropertyFilter.gt(timestampKey, from)
                )
              )
              .setOrderBy(OrderBy.asc(timestampKey))
              .setLimit(limit)
              .build()
          val results: QueryResults[Entity]  =
            DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency)
          val b                              = Vector.newBuilder[EventEnvelope]
          while (results.hasNext) {
            val next = results.next()
            currentTimestamp = next.getLong(timestampKey)
            b += EventEnvelope(
              Offset.sequence(currentTimestamp),
              next.getString(persistenceIdKey),
              next.getLong(sequenceNrKey),
              datastoreSerializer.deserialize(
                SerializedPayload(
                  next.getBlob(payloadKey).toByteArray,
                  next.getLong(serializerKey).toInt,
                  next.getString(manifestKey)
                )
              ),
              currentTimestamp
            )
          }
          b.result()
        }
      }

    }

}
