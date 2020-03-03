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

import akka.persistence._
import akka.persistence.journal.AsyncRecovery
import scala.concurrent._

trait DatastoreRecovery extends AsyncRecovery { this: DatastoreJournal ⇒

  import  DatastoreJournalObject._

  implicit lazy val replayDispatcher = context.system.dispatchers.lookup(replayDispatcherId)

  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future(highestSequenceNrExecute(persistenceId, fromSequenceNr))
  }

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                         (recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] =  {
    if (getMaxNumber(max) > 0) {
      val entities = replay(persistenceId, fromSequenceNr, toSequenceNr, getMaxNumber(max))
      entities.foreach(persistentRepr => {
        recoveryCallback(persistentRepr)
      })
    }
    Future.successful()

  }

  def getMaxNumber(max: Long): Int = {
    if (max <= Int.MaxValue) {
      max.toInt
    } else {
      Int.MaxValue
    }
  }

 }
