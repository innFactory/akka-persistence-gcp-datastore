package akka.persistence.datastore.journal.read
import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class DatastoreReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal: DatastoreScaladslReadJournal =
    new DatastoreScaladslReadJournal(system, config)

  override val javadslReadJournal: DatastoreJavadslReadJournal =
    new DatastoreJavadslReadJournal(scaladslReadJournal)
}