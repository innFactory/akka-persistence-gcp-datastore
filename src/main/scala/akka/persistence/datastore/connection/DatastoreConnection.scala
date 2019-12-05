package akka.persistence.datastore.connection
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.{Datastore, DatastoreOptions}
import com.typesafe.config.ConfigFactory

object DatastoreConnection {

  // Scopes for Google Credentials
  private val scopeList = java.util.Arrays.asList(
    "https://www.googleapis.com/auth/cloud-platform")

  private val config = ConfigFactory.load()
  private val projectId = config.getString("datastore.projectid")

  private val serviceAccountDatastore =
    getClass()
      .getClassLoader()
      .getResourceAsStream("datastore.json")

  private val options: DatastoreOptions = DatastoreOptions.newBuilder
    .setProjectId(projectId)
    .setCredentials(GoogleCredentials.fromStream(serviceAccountDatastore))
    .build
  private val datastore: Datastore = options.getService

  // Google Datastore Service
  def datastoreService = {
    datastore
  }

}
