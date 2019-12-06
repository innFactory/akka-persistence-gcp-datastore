# GCP Datastore Akka Persistence Plugin

### Setup

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
    
### Testing

To test this plugin 

(Source: https://cloud.google.com/datastore/docs/tools/datastore-emulator)

1. ``` gcloud components install cloud-datastore-emulator ```

2. ``` gcloud beta emulators datastore start --no-store-on-disk ```

3. Set Env Variable ```DATASTORE_TESTHOST=http://<host>:<port>``` of datastore emulator

4. Execute ````sbt run````
