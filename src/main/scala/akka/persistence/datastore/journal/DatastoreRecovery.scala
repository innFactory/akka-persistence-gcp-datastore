package akka.persistence.datastore.journal

import akka.persistence._
import akka.persistence.journal.AsyncRecovery

import scala.concurrent._

trait DatastoreRecovery extends AsyncRecovery { this: DatastoreJournal ⇒

  import  DatastoreJournalObject._

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(replayDispatcherId)

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    Future (highestSequenceNrExecute(persistenceId))

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                         (recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] =  Future {

    val maxNbrOfMessages: Int =
      if (max <= Int.MaxValue) max.toInt
      else Int.MaxValue

    if (maxNbrOfMessages > 0) {
      var entities = replay(persistenceId, fromSequenceNr, toSequenceNr, maxNbrOfMessages)
      entities.foreach(persistentRepr => {
        recoveryCallback(persistentRepr)
      })
    }

  }

 }
