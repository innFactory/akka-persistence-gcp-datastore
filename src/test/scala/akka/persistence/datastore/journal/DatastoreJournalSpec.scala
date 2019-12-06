package akka.persistence.datastore.journal
import akka.actor.ActorSystem
import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

class DatastoreJournalSpec
  extends JournalSpec(
    config = ConfigFactory.parseString("""akka.persistence.journal.plugin = "gcp-datastore-journal"""")) {

  val actorSystem: ActorSystem = ActorSystem("test", config)

  override def supportsRejectingNonSerializableObjects: CapabilityFlag =
    false // or CapabilityFlag.off

  override def supportsSerialization: CapabilityFlag =
    false // or CapabilityFlag.on
}
