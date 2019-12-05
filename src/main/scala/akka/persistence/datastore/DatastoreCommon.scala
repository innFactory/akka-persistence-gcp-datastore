package akka.persistence.datastore

import akka.persistence.datastore.connection.DatastoreConnection
import com.google.cloud.datastore.{Datastore, DatastoreOptions}
import com.typesafe.config.Config

object DatastoreCommon {
  val journalKind = "journal"
  val snapshotKind = "snapshot"
  val persistenceIdKey = "persistenceId"
  val sequenceNrKey = "sequenceNr"
  val messageKey: String = "message"
  val markerKey: String = "marker"
}

trait DatastoreCommon {
  protected val databaseMsg: String = "database"
  protected val collectionMsg: String = "collection"

  protected val configRootKey: String

  protected val config: Config

  val service: Datastore = DatastoreConnection.datastoreService

}

trait DatastoreJournalCommon extends DatastoreCommon {
  override protected val configRootKey: String = "gcp-datastore-journal"
  protected def rejectNonSerializableObjectsKey: String = "reject-non-serializable-objects"
  lazy val rejectNonSerializableObjectId: Boolean = config.getBoolean(rejectNonSerializableObjectsKey)
}
