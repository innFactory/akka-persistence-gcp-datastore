package akka.persistence.datastore.journal

import akka.persistence._
import akka.persistence.journal.AsyncRecovery

import scala.concurrent._

trait DatastoreRecovery extends AsyncRecovery { this: DatastoreJournal ⇒

  import  DatastoreJournalObject._

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(replayDispatcherId)

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future(highestSequenceNrExecute(persistenceId, fromSequenceNr))
  }


  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                         (recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] =  {


    if (getMaxNumber(max) > 0) {
      val entities = replay(persistenceId, fromSequenceNr, toSequenceNr, getMaxNumber(max))
      entities.foreach(persistentRepr => {
        // println("RECOVERY " + persistentRepr.payload.getClass.toString)
        recoveryCallback(persistentRepr)
      })
      Future.successful()
    } else {
      Future.successful()
    }

  }

  def getMaxNumber(max: Long): Int = {
    if (max <= Int.MaxValue) {
      max.toInt
    } else {
      Int.MaxValue
    }
  }

 }
