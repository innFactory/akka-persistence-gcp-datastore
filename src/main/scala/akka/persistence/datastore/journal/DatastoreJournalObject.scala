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

package akka.persistence.datastore.journal

import java.util.UUID
import akka.actor.ActorLogging
import akka.persistence.PersistentRepr
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore._
import akka.persistence.datastore.serialization.{ DatastoreSerializer, SerializedPayload }
import akka.persistence.journal.Tagged
import com.fasterxml.uuid.Generators
import com.google.cloud.datastore.StructuredQuery.{ CompositeFilter, OrderBy, PropertyFilter }
import com.google.cloud.datastore._
import scala.concurrent.{ ExecutionContext }
import scala.util.{ Success, Try }

object DatastoreJournalObject {

  private val sequenceNrKey    = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val writerUUIDKey    = DatastoreCommon.writerUUID
  private val markerKey        = DatastoreCommon.markerKey
  private val payloadKey       = DatastoreCommon.payloadKey
  private val kind             = DatastoreCommon.journalKind
  private val tagsKey          = DatastoreCommon.tagsKey
  private val timeBasedUUIDKey = DatastoreCommon.timeBasedUUIDKey
  private val timestampKey     = DatastoreCommon.timestampKey
  private val serializerKey    = DatastoreCommon.serializerKey
  private val manifestKey      = DatastoreCommon.manifestKey

  def persistentReprToDatastoreEntity(
    persistentRepr: PersistentRepr,
    tagList: List[String],
    f: Any => SerializedPayload
  )(implicit rejectNonSerializableObjects: Boolean): Try[Entity] = {
    val uuid             = UUID.randomUUID()
    val timeBasedUUID    = Generators.timeBasedGenerator().generate()
    val keyFactory       = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
    val key              = keyFactory.newKey(uuid.toString)
    def marker(): String = if (persistentRepr.deleted) "D" else ""
    def tagListToValueList: ListValue = {
      val lv: ListValue.Builder = ListValue.newBuilder()
      tagList.foreach(t => lv.addValue(t))
      lv.build()
    }
    def toEntity(value: SerializedPayload) = {
      val dataString: Blob = Blob.copyFrom(value.data)
      Entity
        .newBuilder(key)
        .set(payloadKey, BlobValue.newBuilder(dataString).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, persistentRepr.persistenceId)
        .set(sequenceNrKey, persistentRepr.sequenceNr)
        .set(markerKey, marker())
        .set(writerUUIDKey, persistentRepr.writerUuid)
        .set(tagsKey, tagListToValueList)
        .set(timeBasedUUIDKey, timeBasedUUID.toString)
        .set(timestampKey, timeBasedUUID.timestamp())
        .set(serializerKey, value.serializerId.toLong)
        .set(manifestKey, value.manifest)
        .build
    }
    val payload = persistentRepr.payload match {
      case tagged: Tagged => tagged.payload
      case a              => a
    }
    Success(toEntity(f(payload)))

  }

  def datastoreEntityToPersistentRepr(
    persistenceEntity: Entity,
    f: SerializedPayload => Any
  ): Option[PersistentRepr] = {
    if (persistenceEntity.getString(markerKey) == "D") return None
    val payload = persistenceEntity.getBlob(payloadKey)
    val persistenceRepr = PersistentRepr.apply(
      payload = f(
        SerializedPayload(
          payload.toByteArray,
          persistenceEntity.getLong(serializerKey).toInt,
          persistenceEntity.getString(manifestKey)
        )
      ),
      persistenceId = persistenceEntity.getString(persistenceIdKey),
      sequenceNr = persistenceEntity.getLong(sequenceNrKey),
      deleted = persistenceEntity.getString(markerKey).equals("D"),
      writerUuid = persistenceEntity.getString(writerUUIDKey)
    )
    Some(
      persistenceRepr
    )
  }

  def highestSequenceNrExecute(persistenceId: String, fromSequenceNr: Long): Long = {
    val query: StructuredQuery[Entity] =
      Query
        .newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(PropertyFilter.eq(persistenceIdKey, persistenceId))
        .setOrderBy(OrderBy.desc(sequenceNrKey))
        .setLimit(1)
        .build()
    val results: QueryResults[Entity] =
      DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
    if (results.hasNext) {
      val e: Entity = results.next
      e.getLong(sequenceNrKey)
    } else {
      0L
    }
  }

  def replayExecute(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    maxNumberOfMessages: Int,
    f: SerializedPayload => Any
  ): Seq[PersistentRepr] = {
    val query: StructuredQuery[Entity] =
      Query
        .newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(
          CompositeFilter.and(
            PropertyFilter.eq(persistenceIdKey, persistenceId),
            PropertyFilter.ge(sequenceNrKey, fromSequenceNr),
            PropertyFilter.le(sequenceNrKey, toSequenceNr)
          )
        )
        .setOrderBy(OrderBy.desc(sequenceNrKey))
        .build()
    val results: QueryResults[Entity] = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency)

    var result: Seq[Entity] = Seq.empty[Entity]
    while (results.hasNext) {
      result = results.next +: result
    }
    val messagesToReplay =
      result.take(maxNumberOfMessages).flatMap(dbObject => datastoreEntityToPersistentRepr(dbObject, f))
    messagesToReplay
  }

  def persistExecute(entities: List[Entity])(implicit ec: ExecutionContext): List[Entity] =
    entities.map(e => DatastoreConnection.datastoreService.add(e))

  def asyncDeleteMessagesExecute(persistenceId: String, toSequenceNr: Long): Unit = {
    val query: StructuredQuery[Entity] =
      Query
        .newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(
          CompositeFilter.and(
            PropertyFilter.eq(persistenceIdKey, persistenceId),
            PropertyFilter.le(sequenceNrKey, toSequenceNr)
          )
        )
        .build()
    val results: QueryResults[Entity] =
      DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
    var result: Seq[Entity] = Seq.empty[Entity]
    while (results.hasNext) {
      val x = results.next()
      val updatedEntity = Entity
        .newBuilder(x.getKey)
        .set(payloadKey, BlobValue.newBuilder(x.getBlob(payloadKey)).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, x.getString(persistenceIdKey))
        .set(sequenceNrKey, x.getLong(sequenceNrKey))
        .set(markerKey, "D")
        .set(writerUUIDKey, x.getString(writerUUIDKey))
        .build
      result = updatedEntity +: result
    }
    DatastoreConnection.datastoreService.update(result: _*)
  }

}

trait DatastoreJournalObject extends DatastorePersistence with DatastoreJournalCommon { mixin: ActorLogging =>

  import DatastoreJournalObject._

  private val replayDispatcherKey: String       = "replay-dispatcher"
  protected lazy val replayDispatcherId: String = config.getString(replayDispatcherKey)
  val uuid: UUID                                = UUID.randomUUID()
  val keyFactory: KeyFactory                    = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
  val key: Key                                  = keyFactory.newKey(uuid.toString)
  lazy val datastoreSerializer                  = new DatastoreSerializer(actorSystem)

  protected def persistentReprToDBObject(persistentRepr: PersistentRepr, tagList: List[String])(
    implicit rejectNonSerializableObjects: Boolean
  ): Try[Entity] =
    persistentReprToDatastoreEntity(persistentRepr, tagList, datastoreSerializer.serialize)

  def replay(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    maxNumberOfMessages: Int
  ): Seq[PersistentRepr] =
    replayExecute(persistenceId, fromSequenceNr, toSequenceNr, maxNumberOfMessages, datastoreSerializer.deserialize)

}
