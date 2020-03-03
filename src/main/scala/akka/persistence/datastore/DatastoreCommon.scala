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

package akka.persistence.datastore
import akka.persistence.datastore.connection.DatastoreConnection
import com.google.cloud.datastore.Datastore
import com.typesafe.config.Config

object DatastoreCommon {
  val journalKind              = "journal"
  val snapshotKind             = "snapshot"
  val persistenceIdKey         = "persistenceId"
  val sequenceNrKey            = "sequenceNr"
  val timestampKey             = "timestamp"
  val messageKey: String       = "message"
  val markerKey: String        = "marker"
  val payloadKey: String       = "payload"
  val writerUUID: String       = "writerUUID"
  val tagsKey: String          = "tagsKey"
  val timeBasedUUIDKey: String = "timeBasedUUIDKey"
  val manifestKey: String      = "manifestKey"
  val serializerKey: String    = "serializerKey"

}

trait DatastoreCommon {
  protected val databaseMsg: String   = "database"
  protected val collectionMsg: String = "collection"

  protected val configRootKey: String

  protected val config: Config

  val service: Datastore = DatastoreConnection.datastoreService

}

trait DatastoreJournalCommon extends DatastoreCommon {
  override protected val configRootKey: String          = "gcp-datastore-journal"
  protected def rejectNonSerializableObjectsKey: String = "reject-non-serializable-objects"
  lazy val rejectNonSerializableObjectId: Boolean       = config.getBoolean(rejectNonSerializableObjectsKey)
}

trait DatastoreSnapshotCommon extends DatastoreCommon {
  override protected val configRootKey: String = "gcp-datastore-snapshot"
  val snapshotKey: String                      = "snapshot"
  val timestampKey                             = "timestamp"
}
