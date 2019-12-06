package akka.persistence.datastore.journal
import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import akka.persistence.query.scaladsl.ReadJournal
import com.typesafe.config.Config


class DatastoreReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  override val scaladslReadJournal: DatastoreReadJournal =
    new DatastoreReadJournal(system, config)

  override val javadslReadJournal: DatastoreJavadslReadJournal =
    new DatastoreJavadslReadJournal(scaladslReadJournal)
}

class DatastoreReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with akka.persistence.query.scaladsl.EventsByTagQuery
  with akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
  with akka.persistence.query.scaladsl.PersistenceIdsQuery
  with akka.persistence.query.scaladsl.CurrentPersistenceIdsQuery{

}

class DatastoreJavadslReadJournal(scaladslReadJournal: DatastoreReadJournal) extends ReadJournal
  with akka.persistence.query.javadsl.EventsByTagQuery
  with akka.persistence.query.javadsl.EventsByPersistenceIdQuery
  with akka.persistence.query.javadsl.PersistenceIdsQuery
  with akka.persistence.query.javadsl.CurrentPersistenceIdsQuery{

}