package akka.persistence.datastore.journal

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence._
import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import akka.persistence.serialization.MessageFormats
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

  initialize()

  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {

    val messagesToTryAndPersist: immutable.Seq[Try[Entity]] = messages.flatMap(message => message.payload.map(a => persistentReprToDatastoreEntity(a, persistentReprGetTags(a), serialise)))
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
