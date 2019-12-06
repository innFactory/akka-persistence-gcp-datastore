package akka.persistence.datastore.snapshot
import akka.actor.ActorLogging
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.{DatastoreCommon, DatastorePersistence, DatastoreSnapshotCommon}
import akka.persistence.{SelectedSnapshot, SnapshotMetadata}
import com.google.cloud.datastore.{Blob, BlobValue, Entity}


trait DatastoreSnapshotObject extends DatastorePersistence
  with DatastoreSnapshotCommon { mixin : ActorLogging =>

    import akka.persistence.datastore.DatastoreCommon._
    private val kind = DatastoreCommon.snapshotKind
    private val loadAttemptsKey: String = "load-attempts"

    protected lazy val loadAttempts: Int = config.getInt(loadAttemptsKey)

    override protected def initialize(): Unit = {
      1
    }

    protected def snapshotToDbObject(metadata: SnapshotMetadata, snapshot: Any): Entity = {
      val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
      val key = keyFactory.newKey(metadata.timestamp+metadata.sequenceNr+metadata.persistenceId)
      val dataString: Blob = Blob.copyFrom(serialise(snapshot))
      Entity
        .newBuilder(key)
        .set(payloadKey, BlobValue.newBuilder(dataString).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, metadata.persistenceId)
        .set(sequenceNrKey, metadata.sequenceNr)
        .set(timestampKey, metadata.timestamp)
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
        deserialise(data.toByteArray)
      )
      Some(snapshot)
    }

  }

