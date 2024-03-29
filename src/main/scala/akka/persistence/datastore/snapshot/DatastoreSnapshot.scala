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

package akka.persistence.datastore.snapshot
import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.datastore.DatastoreCommon
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, OrderBy, PropertyFilter}
import com.typesafe.config.Config

import scala.concurrent._

private[snapshot] class DatastoreSnapshot extends SnapshotStore with DatastoreSnapshotObject with ActorLogging {

  import context.dispatcher

  override val actorSystem: ActorSystem = context.system
  private val kind = DatastoreCommon.snapshotKind
  private val sequenceNrKey = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  override val config: Config = context.system.settings.config.getConfig(configRootKey)

  def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    Future {
      val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
      val key = keyFactory.newKey(s"${metadata.timestamp + metadata.sequenceNr}${metadata.persistenceId}")
      DatastoreConnection.datastoreService.delete(key)
      val query: StructuredQuery[Entity] =
        Query
          .newEntityQueryBuilder()
          .setKind(kind)
          .setFilter(
            CompositeFilter.and(
              PropertyFilter.eq(persistenceIdKey, metadata.persistenceId),
              PropertyFilter.eq(sequenceNrKey, metadata.sequenceNr)
            )
          )
          .build()
      val results: QueryResults[Entity] =
        DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
      var result: Seq[Key] = Seq.empty[Key]
      while (results.hasNext)
        result = results.next.getKey +: result
      DatastoreConnection.datastoreService.delete(result: _*)
    }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    val query: StructuredQuery[Entity] =
      Query
        .newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(
          CompositeFilter.and(
            PropertyFilter.eq(persistenceIdKey, persistenceId),
            PropertyFilter.le(timestampKey, criteria.maxTimestamp),
            PropertyFilter.ge(timestampKey, criteria.minTimestamp)
          )
        )
        .build()
    val results: QueryResults[Entity] =
      DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
    var result: Seq[Key] = Seq.empty[Key]
    while (results.hasNext) {
      val next = results.next()
      if (
        next.getLong(sequenceNrKey) <= criteria.maxSequenceNr && next
          .getLong(sequenceNrKey) >= criteria.minSequenceNr
      )
        result = next.getKey +: result
    }
    val res = DatastoreConnection.datastoreService.delete(result: _*)
    Future(res)
  }

  // Select the youngest of {n} snapshots that match the upper bound. This helps where a snapshot may not have
  // persisted correctly because of a JVM crash. As a result an attempt to load the snapshot may fail but an older
  // may succeed.
  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    try {

      val query: StructuredQuery[Entity] =
        Query
          .newEntityQueryBuilder()
          .setKind(kind)
          .setFilter(
            CompositeFilter.and(
              PropertyFilter.eq(persistenceIdKey, persistenceId),
              PropertyFilter.le(timestampKey, criteria.maxTimestamp),
              PropertyFilter.ge(timestampKey, criteria.minTimestamp)
            )
          )
          .setOrderBy(OrderBy.desc(timestampKey))
          .build()
      val results: QueryResults[Entity] =
        DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())

      var result: Seq[Entity] = Seq.empty[Entity]
      while (results.hasNext) {
        val next = results.next()
        if (
          next.getLong(sequenceNrKey) <= criteria.maxSequenceNr && next.getLong(sequenceNrKey) >= criteria.minSequenceNr
        )
          result = result :+ next
      }
      val messagesToReplay = result.take(loadAttempts).flatMap(dbObject => dbObjectToSelectedSnapshot(dbObject))
      Future(messagesToReplay.headOption)
    } catch {
      case e: Exception =>
        println("LOAD SNAPSHOT FAILED " + e.toString)
        Future.failed(e)
    }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    Future {
      DatastoreConnection.datastoreService.put(snapshotToDbObject(metadata, snapshot))
      ()
    }

  override def postStop(): Unit = {}
}
