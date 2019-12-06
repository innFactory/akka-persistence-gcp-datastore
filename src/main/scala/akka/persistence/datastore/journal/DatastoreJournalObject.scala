package akka.persistence.datastore.journal

import java.util.UUID

import akka.actor.ActorLogging
import akka.persistence.PersistentRepr
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore._
import com.google.cloud.datastore.ReadOption.EventualConsistency
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, OrderBy, PropertyFilter}
import com.google.cloud.datastore._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

object DatastoreJournalObject {

  private val sequenceNrKey = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val writerUUIDKey = DatastoreCommon.writerUUID
  private val markerKey = DatastoreCommon.markerKey
  private val payloadKey = DatastoreCommon.payloadKey
  private val kind = DatastoreCommon.journalKind

  def persistentReprToDatastoreEntity(persistentRepr: PersistentRepr, f: Any => Array[Byte])(implicit rejectNonSerializableObjects: Boolean): Try[Entity] = {

    val errorMsg: String = "Unable to serialize payload for"
    val pidMsg: String = s"PersistenceId: ${persistentRepr.persistenceId}"
    val snrMsg: String = s"SequenceId: ${persistentRepr.sequenceNr}"

    val uuid = UUID.randomUUID()
    val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
    val key = keyFactory.newKey(uuid.toString)
    def marker(): String = if (persistentRepr.deleted) "D" else ""
    def toEntity(value: Array[Byte]) = {
      val dataString: Blob = Blob.copyFrom(value)
      Entity
        .newBuilder(key)
        .set(payloadKey, BlobValue.newBuilder(dataString).setExcludeFromIndexes(true).build())
        .set(persistenceIdKey, persistentRepr.persistenceId)
        .set(sequenceNrKey, persistentRepr.sequenceNr)
        .set(markerKey, marker())
        .set(writerUUIDKey, persistentRepr.writerUuid)
        .build
    }
    Success(toEntity(f(persistentRepr.payload)))

  }

  def datastoreEntityToPersistentRepr(persistenceEntity: Entity, f: Array[Byte] =>
      Any): Option[PersistentRepr] = {
    if (persistenceEntity.getString(markerKey) == "D") return None
    val payload = persistenceEntity.getBlob(payloadKey)
    var persistenceRepr = PersistentRepr.apply(
      payload = f(payload.toByteArray),
      persistenceId = persistenceEntity.getString(persistenceIdKey),
      sequenceNr = persistenceEntity.getLong(sequenceNrKey),
      deleted = persistenceEntity.getString(markerKey).equals("D"),
      writerUuid = persistenceEntity.getString(writerUUIDKey)
    )
    Some(persistenceRepr)
  }


  def highestSequenceNrExecute(persistenceId: String, fromSequenceNr: Long): Long = {
    val query: StructuredQuery[Entity] =
      Query.newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(PropertyFilter.eq(persistenceIdKey, persistenceId))
        .setOrderBy(OrderBy.desc(sequenceNrKey))
        .setLimit(1)
        .build()
    val results: QueryResults[Entity]  = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
    if(results.hasNext) {
      val e: Entity = results.next
      e.getLong(sequenceNrKey)
    } else {
      0L
    }
  }

  def replayExecute(persistenceId: String, fromSequenceNr: Long,
                          toSequenceNr: Long, maxNumberOfMessages: Int, f: Array[Byte] =>
    Any): Seq[PersistentRepr] = {
    val query: StructuredQuery[Entity] =
      Query.newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(CompositeFilter.and(
          PropertyFilter.eq(persistenceIdKey, persistenceId),
          PropertyFilter.ge(sequenceNrKey, fromSequenceNr),
          PropertyFilter.le(sequenceNrKey, toSequenceNr)))
        .setOrderBy(OrderBy.desc(sequenceNrKey))
        .build()
    val results: QueryResults[Entity]  = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency)

    var result: Seq[Entity] = Seq.empty[Entity]
    while(results.hasNext) {
      result = results.next +: result
    }

    val messagesToReplay = result.take(maxNumberOfMessages).map(dbObject => datastoreEntityToPersistentRepr(dbObject, f)).flatten
    println(s"REPLAY Execute ${fromSequenceNr} to ${toSequenceNr} for ${maxNumberOfMessages} resulting ${result.length} and ${messagesToReplay.length}" )
    messagesToReplay
  }


  def persistExecute(entities: List[Entity])(implicit ec: ExecutionContext): List[Entity] = {
    entities.map(e => DatastoreConnection.datastoreService.add(e))
  }

  def asyncDeleteMessagesExecute(persistenceId: String, toSequenceNr: Long): Unit = {
    val query: StructuredQuery[Entity] =
      Query.newEntityQueryBuilder()
        .setKind(kind)
        .setFilter(
        CompositeFilter.and(
          PropertyFilter.eq(persistenceIdKey, persistenceId),
          PropertyFilter.le(sequenceNrKey, toSequenceNr),
        ))
        .build()
    val results: QueryResults[Entity]  = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency())
    var result: Seq[Entity] = Seq.empty[Entity]
    while(results.hasNext) {
      val x = results.next()
      val updatedEntity = Entity.newBuilder(x.getKey)
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

trait  DatastoreJournalObject extends DatastorePersistence
  with DatastoreJournalCommon { mixin : ActorLogging =>

  import DatastoreJournalObject._

  private val replayDispatcherKey: String = "replay-dispatcher"
  protected lazy val replayDispatcherId: String = config.getString(replayDispatcherKey)
  val uuid = UUID.randomUUID()
  val keyFactory = DatastoreConnection.datastoreService.newKeyFactory.setKind(kind)
  val key = keyFactory.newKey(uuid.toString)

  override protected def initialize(): Unit = {
    1
  }

  protected def persistentReprToDBObject(persistentRepr: PersistentRepr)
    (implicit rejectNonSerializableObjects: Boolean): Try[Entity] =
    persistentReprToDatastoreEntity(persistentRepr, serialise)


  def replay(persistenceId: String, fromSequenceNr: Long,
                   toSequenceNr: Long, maxNumberOfMessages: Int): Seq[PersistentRepr] = {
    replayExecute(persistenceId, fromSequenceNr, toSequenceNr, maxNumberOfMessages, deserialise)
  }

}
