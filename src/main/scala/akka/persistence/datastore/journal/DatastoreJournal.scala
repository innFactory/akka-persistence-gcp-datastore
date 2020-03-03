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

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence._
import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import com.google.cloud.datastore.Entity
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

private[journal] class DatastoreJournal extends AsyncWriteJournal
  with DatastoreJournalObject
  with DatastoreRecovery
  with ActorLogging {

  import DatastoreJournalObject._

  override val actorSystem: ActorSystem = context.system
  override val config: Config = context.system.settings.config.getConfig(configRootKey)
  implicit val rejectNonSerializableObjects: Boolean = rejectNonSerializableObjectId

  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    val messagesToTryAndPersist: immutable.Seq[Try[Entity]] = messages.flatMap(message => message.payload.map(a => persistentReprToDatastoreEntity(a, persistentReprGetTags(a), datastoreSerializer.serialize)))
    val persistedMessages: Future[List[Entity]] = Future(persistExecute(messagesToTryAndPersist.flatMap(_.toOption).toList))
    val promise = Promise[immutable.Seq[Try[Unit]]]()
    persistedMessages.onComplete {
      case Success(_) if messagesToTryAndPersist.exists(_.isFailure)  =>
        promise.success(messagesToTryAndPersist.map(_ match {
          case Success(_) => Success((): Unit)
          case Failure(error) => Failure(error)
        }))
      case Success(_) =>
        promise.complete(Success(Nil))
      case Failure(e) => promise.failure(e)
    }
    promise.future

  }

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future(asyncDeleteMessagesExecute(persistenceId, toSequenceNr))
  }

  private def persistentReprGetTags(persistentRepr: PersistentRepr): List[String] = {
    persistentRepr.payload match {
      case t: Tagged => {
        t.tags.toList
      }
      case _ =>
       List.empty[String]
    }
  }


}
