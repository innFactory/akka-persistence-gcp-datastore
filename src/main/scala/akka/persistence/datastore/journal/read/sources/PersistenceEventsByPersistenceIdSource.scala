package akka.persistence.datastore.journal.read.sources
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.persistence.datastore.DatastoreCommon
import akka.persistence.datastore.connection.DatastoreConnection
import akka.persistence.datastore.serialization.{DatastoreSerializer, SerializedPayload}
import akka.persistence.query._
import akka.stream.{ActorAttributes, Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, OrderBy, PropertyFilter}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


class PersistenceEventsByPersistenceIdSource(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, uuid: UUID, refreshInterval: FiniteDuration, system: ExtendedActorSystem)
  extends GraphStage[SourceShape[EventEnvelope]] {

  private val datastoreSerializer = new DatastoreSerializer(system)

  private case object Continue
  val out: Outlet[EventEnvelope] = Outlet(
    "PersistenceEventsByPersistenceIdSource.out"
  )
  override def shape: SourceShape[EventEnvelope] = SourceShape(out)
  private val sequenceNrKey = DatastoreCommon.sequenceNrKey
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val payloadKey = DatastoreCommon.payloadKey
  private val kind = DatastoreCommon.journalKind
  private val tagsKey = DatastoreCommon.tagsKey
  private val serializerKey = DatastoreCommon.serializerKey
  private val manifestKey = DatastoreCommon.manifestKey

  override protected def initialAttributes: Attributes =
    Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      private val Limit = 1000
      private var buf = Vector.empty[EventEnvelope]

      override def preStart(): Unit = {
        scheduleWithFixedDelay(Continue, refreshInterval, refreshInterval)
      }

      override def onPull(): Unit = {
        query()
        tryPush()
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          query()
          tryPush()
        }
      })

      override def onDownstreamFinish(): Unit = {
        // close connection if responsible for doing so
      }

      private def query(): Unit = {
        if (buf.isEmpty) {
          try {
            buf =
              Select.run(persistenceId, fromSequenceNr, toSequenceNr, Limit)
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
        def run(id: String,
                from: Long,
                to: Long,
                limit: Int): Vector[EventEnvelope] = {
          try {
            val query: StructuredQuery[Entity] =
              Query
                .newEntityQueryBuilder()
                .setKind(kind)
                .setFilter(
                  CompositeFilter.and(
                    PropertyFilter.eq(persistenceIdKey, id),
                    PropertyFilter.ge(sequenceNrKey, from),
                    PropertyFilter.le(sequenceNrKey, to),
                  )
                )
                .setOrderBy(OrderBy.asc(sequenceNrKey))
                .setLimit(limit)
                .build()
            val results: QueryResults[Entity] =
              DatastoreConnection.datastoreService
                .run(query, ReadOption.eventualConsistency)
            val b = Vector.newBuilder[EventEnvelope]
            while (results.hasNext) {
              val next = results.next()
              b += EventEnvelope(
                NoOffset,
                next.getString(persistenceIdKey),
                next.getLong(sequenceNrKey),
                datastoreSerializer.deserialize(
                  SerializedPayload(
                    next.getBlob(payloadKey).toByteArray,
                    next.getLong(serializerKey).toInt,
                    next.getString(manifestKey)
                  )
                )
              )
            }
            b.result()
          }
        }
      }
    }
  }


