# GCP Datastore Akka Persistence Plugin

[![CircleCI](https://circleci.com/gh/innFactory/akka-persistence-gcp-datastore/tree/master.svg?style=svg&circle-token=700c4e7e6802d0dacbe552c87f25454e20f6ce28)](https://circleci.com/gh/innFactory/akka-persistence-gcp-datastore/tree/master)
[ ![Download](https://api.bintray.com/packages/innfactory/maven/akka-persistence-gcp-datastore/images/download.svg) ](https://bintray.com/innfactory/maven/akka-persistence-gcp-datastore/_latestVersion)

## Setup

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
    
## Testing

To test this plugin 

(Source: https://cloud.google.com/datastore/docs/tools/datastore-emulator)

1. ``` gcloud components install cloud-datastore-emulator ```

2. ``` gcloud beta emulators datastore start --no-store-on-disk --consistency=1.0 ```

3. Set Env Variable ```DATASTORE_TESTHOST=http://<host>:<port>``` of datastore emulator

4. Execute ````sbt run````

5. Before executing test reset datastore data: ```curl -X POST http://<host>:<port>/reset```

## Contribution policy
Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License
This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Credits
[innFactory GmbH](https://innfactory.de)
