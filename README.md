# GCP Datastore Akka Persistence Plugin

[![CircleCI](https://circleci.com/gh/innFactory/akka-persistence-gcp-datastore/tree/master.svg?style=svg&circle-token=700c4e7e6802d0dacbe552c87f25454e20f6ce28)](https://circleci.com/gh/innFactory/akka-persistence-gcp-datastore/tree/master)
[ ![Download](https://api.bintray.com/packages/innfactory/sbt-plugins/akka-persistence-gcp-datastore/images/download.svg) ](https://bintray.com/innfactory/sbt-plugins/akka-persistence-gcp-datastore/_latestVersion)
[![shields.io](http://img.shields.io/badge/license-Apache2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e906cfd4a16f46d3b049c404b9d5fa55)](https://www.codacy.com/gh/innFactory/akka-persistence-gcp-datastore?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=innFactory/akka-persistence-gcp-datastore&amp;utm_campaign=Badge_Grade)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

akka-persistence-gcp-datastore is a journal and snapshot store plugin for [akka-persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html) using [Google Cloud Datastore](https://cloud.google.com/datastore).
It uses the official Google Java Dependency to talk with the datastore. 

Scala 2.12 & 2.13, Java 8 & Java 11, akka 2.6.X are supported.

The plugin supports the following functionality:
* serialization of events and snapshots with play-json
* [peristence-query](http://doc.akka.io/docs/akka/current/scala/persistence-query.html) api
* custom serialization 

## Related Blogposts

- [Using Google Datastore with akka-persistence](https://medium.com/@innFactory/using-google-datastore-with-akka-persistence-1f6f7179b22a)
- [Was ist Event Sourcing? (de)](https://innfactory.de/softwareentwicklung/scala-akka-play-co/was-ist-event-sourcing-und-wie-funktioniert-es/)
- [Wie lässt sich das Lesen beim Event Sourcing durch CQRS optimieren? (de)](https://innfactory.de/softwareentwicklung/wie-laesst-sich-das-lesen-beim-event-sourcing-durch-cqrs-optimieren/)


## Usage & Setup 

Versions: The table below lists the versions and their main dependencies

| Version to use 	| Scala 2.12 	| Scala 2.13 	| Scala 3 / Dotty 	| Akka  	| play-json 	| google-cloud-datastore 	|
|----------------	|------------	|------------	|-----------------	|-------	|-----------	|------------------------	|
| 1.0.1          	| ✓          	| ✓          	| ?               	| 2.6.x 	| 2.8.x     	| 1.102.x                	|



### Dependency

You just need to add the following dependency to you sbt dependencies
```
libraryDependencies += "de.innfactory" %% "akka-persistence-gcp-datastore" % "X.Y.Z"
```

### Configuration
Take a look at reference.conf under src/main/resources
We forked the cqrs cassandra lightbend example with necessary changes for gcp-datastore (@ Demo Test Project available based on the CQRS Example from Lightbend https://github.com/innFactory/akka-persistence-gcp-datastore-example)

Add the following to your application.conf for a basic configuration:

```
akka {
  # use google cloud datastore as journal and snapshot store
  persistence {

    journal {
      plugin = "gcp-datastore-journal"
      auto-start-journals = ["gcp-datastore-journal"]
    }

    snapshot-store {
      plugin = "gcp-datastore-snapshot"
      auto-start-snapshot-stores = ["gcp-datastore-snapshot"]
    }
  }
}
```

### Datastore Configuration

1. Google Cloud Project with Datastore or FireStore in Datastore mode enabled  
2. Create a index.yml file with content bolow in the project that will use this plugin:

    ```
    indexes:
      - kind: journal
        properties:
          - name: persistenceId
          - name: sequenceNr
            direction: desc
    
      - kind: snapshot
        properties:
          - name: persistenceId
          - name: timestamp
            direction: desc
    
      - kind: snapshot
        properties:
          - name: persistenceId
          - name: timestamp
    
      - kind: journal
        properties:
        - name: tagsKey
        - name: timestamp
    
      - kind: journal
        properties:
        - name: persistenceId
        - name: sequenceNr
    ```
    index.yml

3. Open terminal and execute 
 
    ```
    gcloud app deploy index.yaml
    ```
    This is telling the GCP Datastore to build indexes for the plugin based on the yaml file
    
4. Create a service account for read and write to datastore. Download the json and add it to the project

    ```
    src/main/resources/datastore.json
    ```

## Persistence query API

The plugin supports the Persistence query APi, mostly used in CQRS applications to transform/migrate the events from the write side to the read side.

The ReadJournal is retrieved via the `akka.persistence.datastore.journal.read.DatastoreScaladslReadJournal` and `akka.persistence.datastore.journal.read.DatastoreJavadslReadJournal`. There is also a DatastoreReadJournalProvider.

```scala
import akka.persistence.datastore.journal.read.DatastoreScaladslReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }

val system = ??? //ActorSystem akka-classic or akka-typed then system.toClassic is needed. see the example.
val readJournal =
    PersistenceQuery(system).readJournalFor[DatastoreScaladslReadJournal]("gcp-datastore-query")

```

#### Supported Queries
All queries are live streams and they are not completed when they reaches the end of the currently stored events, but continue to push new events when new events are persisted.

##### eventsByTag
eventsByTags is used for retrieving events that were marked with a given tag.

##### eventsByPersistenceId
eventsByPersistenceId is used for retrieving events for a specific PersistentActor identified by its persistenceId
    
## Testing

To test this plugin 

(Source: https://cloud.google.com/datastore/docs/tools/datastore-emulator)

1. ``` gcloud components install cloud-datastore-emulator ```

2. ``` gcloud beta emulators datastore start --no-store-on-disk --consistency=1.0 ```

3. Set Env Variable ```DATASTORE_TESTHOST=http://<host>:<port>``` of datastore emulator

4. Execute ````sbt run````

5. Before executing test reset datastore data: ```curl -X POST http://<host>:<port>/reset```

There is a shell script under .circle ci which runs all of these tests. cqrs tests are outsourced in the example project.

## Contribution policy
Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## Credits
[innFactory GmbH](https://innfactory.de) is a lightbend partner from germany. We are experts for Apps, BigData & Cloud Computing. If you need help with your next project, feel free to ask for our support. 


