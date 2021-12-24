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
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import akka.stream.{ActorAttributes, Attributes, Outlet, SourceShape}
import com.google.cloud.datastore._

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class PersistenceIdsSource(refreshInterval: FiniteDuration, system: ExtendedActorSystem)
    extends GraphStage[SourceShape[String]] {

  private case object Continue
  val out: Outlet[String] = Outlet(
    "PersistenceIdsSource.out"
  )
  override def shape: SourceShape[String] = SourceShape(out)
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val kind = DatastoreCommon.journalKind

  override protected def initialAttributes: Attributes =
    Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      private val Limit = 1000
      private var buf = Vector.empty[String]

      private var contained = List.empty[String]
      private val lastCursor: Cursor = null

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
          try buf = Select.run(Limit)
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
        def run(limit: Int): Vector[String] = {
          val query: StructuredQuery[ProjectionEntity] = {
            val q = Query
              .newProjectionEntityQueryBuilder()
              .setKind(kind)

            if (lastCursor != null)
              q.setStartCursor(lastCursor)
            q.setLimit(limit)
              .build()
          }

          val results: QueryResults[ProjectionEntity] =
            DatastoreConnection.datastoreService
              .run(query, ReadOption.eventualConsistency)
          val b = Vector.newBuilder[String]
          while (results.hasNext) {
            val next = results.next()
            if (!contained.contains(next.getString(persistenceIdKey))) {
              contained = contained :+ next.getString(persistenceIdKey)
              b += next.getString(persistenceIdKey)
            }
          }
          b.result()
        }
      }
    }
}
