package akka.persistence.datastore.snapshot
import akka.actor.ActorLogging
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.serialization.{DatastoreSerializer, SerializedSnapshot}
import akka.persistence.serialization.Snapshot
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

    override protected def initialize(): Unit = {
      1
    }

    protected def snapshotToDbObject(metadata: SnapshotMetadata, snapshot: Any): Entity = {
      val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
      val key = keyFactory.newKey(metadata.timestamp+metadata.sequenceNr+metadata.persistenceId)
      val serializedSnapshot = datastoreSerializer.serializeSnapshot(snapshot)
      val dataString: Blob = Blob.copyFrom(serializedSnapshot.data)
      Entity
        .newBuilder(key)
        .set(payloadKey, BlobValue.newBuilder(dataString).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, metadata.persistenceId)
        .set(sequenceNrKey, metadata.sequenceNr)
        .set(timestampKey, metadata.timestamp)
        .set(serializerKey, serializedSnapshot.serializerId)
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

