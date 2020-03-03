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
import akka.actor.ActorLogging
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.serialization.{DatastoreSerializer, SerializedSnapshot}
import akka.persistence.datastore.{DatastoreCommon, DatastorePersistence, DatastoreSnapshotCommon}
import akka.persistence.{SelectedSnapshot, SnapshotMetadata}
import com.google.cloud.datastore.{Blob, BlobValue, Entity}


trait DatastoreSnapshotObject extends DatastorePersistence
  with DatastoreSnapshotCommon { mixin : ActorLogging =>

    import akka.persistence.datastore.DatastoreCommon._
    private val kind = DatastoreCommon.snapshotKind
    private val loadAttemptsKey: String = "load-attempts"
    private lazy val datastoreSerializer = new DatastoreSerializer(actorSystem)
    protected lazy val loadAttempts: Int = config.getInt(loadAttemptsKey)

    protected def snapshotToDbObject(metadata: SnapshotMetadata, snapshot: Any): Entity = {
      val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
      val key = keyFactory.newKey(s"${metadata.timestamp+metadata.sequenceNr}${metadata.persistenceId}")
      val serializedSnapshot = datastoreSerializer.serializeSnapshot(snapshot)
      val dataString: Blob = Blob.copyFrom(serializedSnapshot.data)
      Entity
        .newBuilder(key)
        .set(payloadKey, BlobValue.newBuilder(dataString).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, metadata.persistenceId)
        .set(sequenceNrKey, metadata.sequenceNr)
        .set(timestampKey, metadata.timestamp)
        .set(serializerKey, serializedSnapshot.serializerId.toLong)
        .set(manifestKey, serializedSnapshot.manifest)
        .build
    }


    def dbObjectToSelectedSnapshot(entity: Entity): Option[SelectedSnapshot] = {
      val data = entity.getBlob(payloadKey)
      val snapshot = SelectedSnapshot(
        SnapshotMetadata(
          entity.getString(persistenceIdKey),
          entity.getLong(sequenceNrKey),
          entity.getLong(timestampKey),
        ),
        datastoreSerializer.deserializeSnapshot(
          SerializedSnapshot(
            data.toByteArray,
            entity.getLong(serializerKey).toInt,
            entity.getString(manifestKey)
          )
        )
      )
      Some(snapshot)
    }

  }

