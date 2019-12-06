package akka.persistence.datastore.snapshot
import akka.persistence.CapabilityFlag
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class DatastoreSnapshotSpec
  extends SnapshotStoreSpec(
    config = ConfigFactory.parseString("""
    akka.persistence.snapshot-store.plugin = "gcp-datastore-snapshot"
    """)) {

  override def supportsSerialization: CapabilityFlag =
    false // or CapabilityFlag.on
}
