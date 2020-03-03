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
import akka.actor.ExtendedActorSystem
import akka.persistence.datastore.journal.read.sources.{CurrentPersistenceIdsSource, PersistenceEventsByPersistenceIdSource, PersistenceEventsByTagSource, PersistenceIdsSource}
import akka.persistence.query._
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class DatastoreScaladslReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with akka.persistence.query.scaladsl.EventsByTagQuery
  with akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
  with akka.persistence.query.scaladsl.PersistenceIdsQuery
  with akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery {

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
      Source.fromGraph(new PersistenceEventsByTagSource(tag, o, refreshInterval, system))
    case NoOffset => eventsByTag(tag, Sequence(0L)) //recursive
    case TimeBasedUUID(value) => Source.fromGraph(new PersistenceEventsByTagSource(tag, value.timestamp(), refreshInterval, system))
    case _ =>
      throw new IllegalArgumentException("Datastore Journal does not support " + offset.getClass.getName + " offsets")
  }

  override def eventsByPersistenceId(
                                      persistenceId: String,
                                      fromSequenceNr: Long,
                                      toSequenceNr: Long
                                    ): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new PersistenceEventsByPersistenceIdSource(persistenceId, fromSequenceNr, toSequenceNr, refreshInterval, system))
  }

  override def persistenceIds(): Source[String, NotUsed] = {
    Source.fromGraph(new PersistenceIdsSource(refreshInterval, system))
  }

  override def currentPersistenceIds(): Source[String, NotUsed] = {
    Source.fromGraph(new CurrentPersistenceIdsSource(refreshInterval, system))
  }

}
