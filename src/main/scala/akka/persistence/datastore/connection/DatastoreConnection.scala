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
  private val localDatastoreHost = config.getString("datastore.testhost")


  val datastore: Datastore = getDatastoreService


  def getDatastoreService : Datastore = {
    if(sys.props.get("testing").getOrElse("false") == "true") {
      // CreateDatastore for test
      DatastoreOptions.newBuilder
        .setHost(localDatastoreHost)
        .setTransportOptions(DatastoreOptions.getDefaultHttpTransportOptions)
        .build
        .getService
    } else {
      // CreateDatastore for prod
      initProductionDatastore
    }
  }

  def initProductionDatastore: Datastore = {
    val serviceAccountDatastore =
    getClass()
      .getClassLoader()
      .getResourceAsStream("datastore.json")
    val options: DatastoreOptions = DatastoreOptions.newBuilder
      .setProjectId(projectId)
      .setCredentials(GoogleCredentials.fromStream(serviceAccountDatastore))
      .build
    options.getService
  }

  // Google Datastore Service
  def datastoreService = {
    datastore
  }

}
