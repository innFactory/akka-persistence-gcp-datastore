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

package akka.persistence.datastore.journal.read
import akka.NotUsed
import akka.persistence.query._
import akka.stream.javadsl.{Source => JavaSource}

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