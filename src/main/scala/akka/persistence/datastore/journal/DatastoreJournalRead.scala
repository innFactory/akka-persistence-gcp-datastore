package akka.persistence.datastore.journal
import java.util.UUID

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.datastore.{DatastoreCommon, DatastorePersistence}
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.journal.DatastoreJournalObject.{kind, persistenceIdKey, sequenceNrKey}
import akka.persistence.query._
import akka.persistence.query.scaladsl.ReadJournal
import akka.serialization.SerializationExtension
import akka.stream.{ActorAttributes, Attributes, Outlet, SourceShape}
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, OrderBy, PropertyFilter}
import com.typesafe.config.Config
import akka.stream.javadsl.{Source => JavaSource}
import com.fasterxml.uuid.Generators

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.control.NonFatal


class DatastoreReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  override val scaladslReadJournal: DatastoreScaladslReadJournal =
    new DatastoreScaladslReadJournal(system, config)

  override val javadslReadJournal: DatastoreJavadslReadJournal =
    new DatastoreJavadslReadJournal(scaladslReadJournal)
}

class DatastoreScaladslReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with akka.persistence.query.scaladsl.EventsByTagQuery
  with akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
  with akka.persistence.query.scaladsl.PersistenceIdsQuery
  with akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery{

  private val refreshInterval: FiniteDuration =
    config.getDuration("refresh-interval", MILLISECONDS).millis


  /**
    * You can use `NoOffset` to retrieve all events with a given tag or retrieve a subset of all
    * events by specifying a `Sequence` `offset`. The `offset` corresponds to an ordered sequence number for
    * the specific tag. Note that the corresponding offset of each event is provided in the
    * [[akka.persistence.query.EventEnvelope]], which makes it possible to resume the
    * stream at a later point from a given offset.
    *
    * The `offset` is exclusive, i.e. the event with the exact same sequence number will not be included
    * in the returned stream. This means that you can use the offset that is returned in `EventEnvelope`
    * as the `offset` parameter in a subsequent query.
    * */
  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] = offset match {
    case Sequence(o) =>
      throw new IllegalArgumentException("Datastore Journal does not support sequence based offsets")
    case NoOffset => eventsByTag(tag, Sequence(0L)) //recursive
    case TimeBasedUUID(value) => Source.fromGraph(new PersistenceEventsByTagSource(tag, value, refreshInterval))
    case _ =>
      throw new IllegalArgumentException("MyJournal does not support " + offset.getClass.getName + " offsets")
  }

  override def eventsByPersistenceId(
  persistenceId: String,
  fromSequenceNr: Long,
  toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

  override def persistenceIds(): Source[String, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

  override def currentPersistenceIds(): Source[String, NotUsed] = {
    // implement in a similar way as eventsByTag
    ???
  }

}

class DatastoreJavadslReadJournal(scaladslReadJournal: DatastoreScaladslReadJournal) extends akka.persistence.query.javadsl.ReadJournal
  with akka.persistence.query.javadsl.EventsByTagQuery
  with akka.persistence.query.javadsl.EventsByPersistenceIdQuery
  with akka.persistence.query.javadsl.PersistenceIdsQuery
  with akka.persistence.query.javadsl.CurrentPersistenceIdsQuery{

  override def eventsByTag(tag: String, offset: Offset = Sequence(0L)): JavaSource[EventEnvelope, NotUsed] =
    scaladslReadJournal.eventsByTag(tag, offset).asJava

  override def eventsByPersistenceId(
                                      persistenceId: String,
                                      fromSequenceNr: Long = 0L,
                                      toSequenceNr: Long = Long.MaxValue):  JavaSource[EventEnvelope, NotUsed] =
    scaladslReadJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def persistenceIds():  JavaSource[String, NotUsed] =
    scaladslReadJournal.persistenceIds().asJava

  override def currentPersistenceIds():  JavaSource[String, NotUsed] =
    scaladslReadJournal.currentPersistenceIds().asJava


}

class PersistenceEventsByTagSource(tag: String, uuid: UUID, refreshInterval: FiniteDuration)
  extends GraphStage[SourceShape[EventEnvelope]] {

  private case object Continue
  val out: Outlet[EventEnvelope] = Outlet("PersistenceEventsByTagSource.out")
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)
  private val sequenceNrKey = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val payloadKey = DatastoreCommon.payloadKey
  private val kind = DatastoreCommon.journalKind
  private val tagsKey = DatastoreCommon.tagsKey
  private val timestampKey = DatastoreCommon.timestampKey
  private val timeBasedUUIDKey = DatastoreCommon.timeBasedUUIDKey

  override protected def initialAttributes: Attributes = Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      lazy val system = materializer.system
      private val Limit = 1000
      private var currenttimeBasedUUID = uuid
      private var currenttimeBasedUUIDStamp = uuid.timestamp()
      private var buf = Vector.empty[EventEnvelope]
      private val serialization = SerializationExtension(system)

      override def preStart(): Unit = {
        scheduleWithFixedDelay(Continue, refreshInterval, refreshInterval)
      }

      override def onPull(): Unit = {
        query()
        tryPush()
      }

      override def onDownstreamFinish(): Unit = {
        // close connection if responsible for doing so
      }

      private def query(): Unit = {
        if (buf.isEmpty) {
          try {
            buf = Select.run(tag, currenttimeBasedUUIDStamp , Limit)
          } catch {
            case NonFatal(e) =>
              failStage(e)
          }
        }
      }

      private def tryPush(): Unit = {
        if (buf.nonEmpty && isAvailable(out)) {
          push(out, buf.head)
          buf = buf.tail
        }
      }

      override protected def onTimer(timerKey: Any): Unit = timerKey match {
        case Continue =>
          query()
          tryPush()
      }

      object Select {
         def run(tag: String, from: Long, limit: Int): Vector[EventEnvelope] = {
          try {
            val query: StructuredQuery[Entity] =
              Query.newEntityQueryBuilder()
                .setKind(kind)
                .setFilter(CompositeFilter.and(
                  PropertyFilter.eq(tagsKey, tag),
                  PropertyFilter.gt(timestampKey, from),
               ))
                .setOrderBy(OrderBy.asc(timestampKey))
                .setLimit(limit)
                .build()
            val results: QueryResults[Entity]  = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency)
            val b = Vector.newBuilder[EventEnvelope]
            while (results.hasNext) {
              val next = results.next()
              currenttimeBasedUUIDStamp =  next.getLong(timeBasedUUIDKey)
              currenttimeBasedUUID = UUID.fromString(next.getString(timestampKey))
              b += EventEnvelope(
                Offset.timeBasedUUID(currenttimeBasedUUID),
                next.getString(persistenceIdKey),
                next.getLong(sequenceNrKey),
                DatastoreCommon.deserialise(next.getBlob(payloadKey).toByteArray))
            }
            b.result()
          }
        }
      }
    }

}

class PersistenceEventsByPersistenceIdSource(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, uuid: UUID, refreshInterval: FiniteDuration)
  extends GraphStage[SourceShape[EventEnvelope]] {

  private case object Continue
  val out: Outlet[EventEnvelope] = Outlet("PersistenceEventsByPersistenceIdSource.out")
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)
  private val sequenceNrKey = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val payloadKey = DatastoreCommon.payloadKey
  private val kind = DatastoreCommon.journalKind
  private val tagsKey = DatastoreCommon.tagsKey

  override protected def initialAttributes: Attributes = Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      lazy val system = materializer.system
      private val Limit = 1000
      private var buf = Vector.empty[EventEnvelope]
      private val serialization = SerializationExtension(system)

      override def preStart(): Unit = {
        scheduleWithFixedDelay(Continue, refreshInterval, refreshInterval)
      }

      override def onPull(): Unit = {
        query()
        tryPush()
      }

      override def onDownstreamFinish(): Unit = {
        // close connection if responsible for doing so
      }

      private def query(): Unit = {
        if (buf.isEmpty) {
          try {
            buf = Select.run(persistenceId, fromSequenceNr, toSequenceNr , Limit)
          } catch {
            case NonFatal(e) =>
              failStage(e)
          }
        }
      }

      private def tryPush(): Unit = {
        if (buf.nonEmpty && isAvailable(out)) {
          push(out, buf.head)
          buf = buf.tail
        }
      }

      override protected def onTimer(timerKey: Any): Unit = timerKey match {
        case Continue =>
          query()
          tryPush()
      }

      object Select {
        def run(id: String, from: Long, to: Long, limit: Int): Vector[EventEnvelope] = {
          try {
            val query: StructuredQuery[Entity] =
              Query.newEntityQueryBuilder()
                .setKind(kind)
                .setFilter(CompositeFilter.and(
                  PropertyFilter.eq(persistenceIdKey, persistenceId),
                  PropertyFilter.ge(sequenceNrKey, from),
                  PropertyFilter.le(sequenceNrKey, to),
                ))
                .setOrderBy(OrderBy.asc(sequenceNrKey))
                .setLimit(limit)
                .build()
            val results: QueryResults[Entity]  = DatastoreConnection.datastoreService.run(query, ReadOption.eventualConsistency)
            val b = Vector.newBuilder[EventEnvelope]
            while (results.hasNext) {
              val next = results.next()
              b += EventEnvelope(
                NoOffset,
                next.getString(persistenceIdKey),
                next.getLong(sequenceNrKey),
                DatastoreCommon.deserialise(next.getBlob(payloadKey).toByteArray))
            }
            b.result()
          }
        }
      }
    }

}