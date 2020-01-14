package akka.persistence.datastore.journal.read.sources

import akka.actor.ExtendedActorSystem
import akka.persistence.datastore.DatastoreCommon
import akka.persistence.datastore.connection.DatastoreConnection
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import akka.stream.{ActorAttributes, Attributes, Outlet, SourceShape}
import com.google.cloud.datastore._

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class CurrentPersistenceIdsSource(refreshInterval: FiniteDuration, system: ExtendedActorSystem)
  extends GraphStage[SourceShape[String]] {

  private case object Continue
  val out: Outlet[String] = Outlet(
    "CurrentPersistenceIdsSource.out"
  )
  override def shape: SourceShape[String] = SourceShape(out)
  private val persistenceIdKey = DatastoreCommon.persistenceIdKey
  private val kind = DatastoreCommon.journalKind

  override protected def initialAttributes: Attributes =
    Attributes(ActorAttributes.IODispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with OutHandler {
      private val Limit = 1000
      private var buf = Vector.empty[String]

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
              Select.run(Limit)
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
        def run(limit: Int): Vector[String] = {
          try {
            val query: StructuredQuery[Entity] =
              Query
                .newEntityQueryBuilder()
                .setKind(kind).setLimit(limit)
                .build()
            val results: QueryResults[Entity] =
              DatastoreConnection.datastoreService
                .run(query, ReadOption.eventualConsistency)
            val b = Vector.newBuilder[String]
            while (results.hasNext) {
              val next = results.next()
              b += next.getString(persistenceIdKey)
            }
            b.result()
          }
        }
      }
    }
  }


