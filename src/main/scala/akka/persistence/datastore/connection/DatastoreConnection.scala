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

package akka.persistence.datastore.connection
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.{Datastore, DatastoreOptions}
import com.typesafe.config.ConfigFactory

object DatastoreConnection {

  private val config = ConfigFactory.load()
  private val projectId = config.getString("datastore.projectid")
  private val localDatastoreHost = config.getString("datastore.testhost")
  private val datastore: Datastore = getDatastoreService

  private def getDatastoreService : Datastore = {
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

  private def initProductionDatastore: Datastore = {
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
  def datastoreService = datastore


}
